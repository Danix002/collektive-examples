package it.unibo.collektive.examples.geoChat.utils

import it.unibo.collektive.aggregate.api.Aggregate
import kotlin.Float.Companion.POSITIVE_INFINITY
import it.unibo.collektive.aggregate.api.neighboring
import it.unibo.collektive.aggregate.api.share
import it.unibo.collektive.stdlib.fields.fold
import it.unibo.collektive.stdlib.spreading.multiGradientCast
import it.unibo.collektive.stdlib.util.Point3D
import it.unibo.collektive.stdlib.util.euclideanDistance3D
import kotlin.collections.mapValues

/**
 * Data class representing the distances from a source device to another non-source device.
 */
data class SourceDistances(
    /**
     * Contains the id of the source node being considered.
    */
    val sender: Int,
    /**
     * Contains the id of the non-source node being considered.
    */
    val receiver: Int,
    /**
     * Message payload
     */
    val text: String,
    /**
     * Contains the distance set by the source to be able to receive its messages.
    */
    val distanceForMessaging: Float,
    /**
     * Contains the distance between node in [sender] value and node in [receiver] value.
    */
    val distance: Double,
    /**
     * An incremental counter that identifies the specific emission sequence of messages
     * originating from the source node.
    */
    val sourceCount: Int,
    /**
     * Is a boolean value indicating whether the identified messaging distance has been
     * communicated by a source node.
    */
    val isSourceValues: Boolean
)

/**
 * Synchronizes and validates spatial message propagation by aligning neighborhood metadata
 * with computed Euclidean distances.
 *
 * This function leverages the alignedMap construct to perform a coordinated computation
 * across the neighborhood. It calculates the 3D distance between the localId and each
 * neighbor while simultaneously unpacking the metadata provided by those neighbors.
 *
 * Each neighbor's state in [devices] is expected to be a [Triple] containing:
 * 1. **Messaging Radius** ([Float]): The maximum distance at which the source's message remains valid.
 * 2. **Payload** ([String]): The actual text content of the message.
 * 3. **Emission Counter** ([Int]): The sourceCount identifying the specific message version.
 *
 * The function produces a [SourceDistances] record for each neighbor-to-neighbor relationship,
 * which is then strictly filtered based on the following criteria:
 * - **Identity**: The sender must be a recognized source in the [senders] map and cannot be the localId.
 * - **Spatial Validity**: The computed Euclidean distance must be less than or equal to the
 * broadcast radius (distanceForMessaging) defined by the source.
 * - **Source Integrity**: The isSourceValues flag must be true, ensuring the data originates
 * from a valid communication branch.
 *
 * @param devices A field-aligned map of neighboring devices and their associated [Triple] metadata.
 * @param position The current 3D coordinates of this node, used to compute [euclideanDistance3D].
 * @param senders A reference map of active message sources used for validation and filtering.
 *
 * @return A map associating each valid neighbor ID with a list of [SourceDistances] that
 * satisfy both spatial proximity and protocol constraints.
 */
fun Aggregate<Int>.saveNewMessage(
    devices:  Map<Int, Triple<Float, String, Int>>,
    position: Point3D,
    senders: Map<Int, Triple<Float, String, Int>>,
) : Map<Int, List<SourceDistances>> {
    return neighboring(devices).alignedMap(euclideanDistance3D(position)) {
        id: Int, deviceValues: Map<Int, Triple<Float, String, Int>>, distance: Double ->
        deviceValues.entries.map { (to, metadata) ->
            SourceDistances(
                to,
                localId,
                metadata.second,
                metadata.first,
                distance,
                metadata.third,
                senders.containsKey(to) &&
                metadata.first != POSITIVE_INFINITY &&
                to != localId
            )
        }
    }.toMap()
        .filterKeys { senders.containsKey(it) && it != localId }
        .mapValues { (key, list) ->
            list.filter { it.isSourceValues && it.distance <= it.distanceForMessaging && it.sender == key}
        }
}

/**
 * Propagates received messages from neighboring nodes using a multi-source gradient,
 * updating the distances and forwarding only messages within the allowed communication radius.
 *
 * This function relies on `multiGradientCast` to perform a distance-based diffusion from
 * multiple sources, leveraging a 3D Euclidean distance metric. The propagation is constrained
 * by each message's `distanceForMessaging`, ensuring that messages do not spread beyond
 * their intended range.
 *
 * @param incomingMessages a map where each key is a source node ID (`Int`), and the corresponding
 *        value is a list of [SourceDistances] representing messages received from that source.
 * @param from a boolean indicating whether the current node is an active message source
 *        in this round. If `true`, the node will be included in the shared source set.
 * @param position the current 3D position of the local node, used to compute distance to neighbors.
 *
 * @return a nested map where the outer keys are neighbor node IDs (`Int`), and the values
 *         are maps associating each message source ID to a filtered list of [SourceDistances].
 *         Each message is updated with the cumulative distance and the current node's ID
 *         as the new intermediate sender (`from`). Messages that exceed their allowed
 *         `distanceForMessaging` are discarded.
 *
 * The returned map excludes empty lists, keeping only meaningful propagated data.
 */
fun Aggregate<Int>.spreadNewMessage(
    incomingMessages: Map<Int, List<SourceDistances>>,
    from: Boolean,
    position: Point3D
) : Map<Int, Map<Int, List<SourceDistances>>> {
    val sources = share(emptySet()) { neighborSources ->
        neighborSources.fold(emptySet()) { accumulated, neighborSet ->
            accumulated union neighborSet.value
        }.let { collected ->
            if (from) collected + localId else collected
        }
    }

    return multiGradientCast(
        sources = sources,
        local = incomingMessages,
        metric = euclideanDistance3D(position),
        accumulateData = { fromSource, toNeighbor, value ->
            value.mapValues { (_, list) ->
                list.mapNotNull {
                    val totalDistance = it.distance + fromSource + toNeighbor
                    if (totalDistance <= it.distanceForMessaging) {
                        it.copy(receiver = localId, distance = totalDistance)
                    } else {
                        null
                    }
                }.filter { it.sender != localId }
            }.filterValues { it.isNotEmpty() }
        },
    )
        .filterKeys { it != localId }
        .filterValues { it.isNotEmpty() }
}
