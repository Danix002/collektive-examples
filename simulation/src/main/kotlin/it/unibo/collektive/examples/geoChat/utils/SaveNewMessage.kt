package it.unibo.collektive.examples.geoChat.utils

import it.unibo.collektive.aggregate.api.Aggregate
import kotlin.Float.Companion.POSITIVE_INFINITY
import it.unibo.collektive.aggregate.api.neighboring
import it.unibo.collektive.stdlib.util.Point3D
import it.unibo.collektive.stdlib.util.euclideanDistance3D

/**
 * Data class representing the distances from a source device to another non-source device.
 */
data class SourceDistances(
    /**
     * Contains the id of the source node being considered.
    */
    val to: Int, 
    /**
     * Contains the id of the non-source node being considered.
    */
    val from: Int, 
    /**
     * Contains the distance set by the source to be able to receive its messages.
    */
    val distanceForMessaging: Float,
    /**
     * Contains the distance between node in [to] value and node in [from] value.
    */
    val distance: Double, 
    /**
     * Is a boolean value indicating whether the identified messaging distance has been
     * communicated by a source node.
    */
    val isSourceValues: Boolean 
)

/**
 * Saves and processes new message information based on devices and their positions.
 *
 * This function analyzes neighboring devices and calculates the alignment of distances
 * between the current node and others using the Euclidean distance metric in 3D space.
 * For each neighboring device, it creates a list of [SourceDistances] representing
 * the relationship between sender and receiver nodes, including distance metrics
 * and whether the message source is valid.
 *
 * The resulting map is filtered to only include entries where the sender is known
 * (contained in [senders]) and excludes the current node itself. For each device,
 * only entries flagged as valid sources (`isSourceValues`) and matching the device ID
 * are retained.
 *
 * @param devices A map of device IDs to float values representing distances or metrics used in neighbor detection.
 * @param position The 3D position of the current node.
 * @param senders A map of sender device IDs to pairs of accumulated distance and message string.
 *
 * @return A filtered map where keys are sender device IDs and values are lists of [SourceDistances]
 * that represent valid message propagation data for that sender.
 */
fun Aggregate<Int>.saveNewMessage(
    devices:  Map<Int, Float>,
    position: Point3D,
    senders: Map<Int, Pair<Float, String>>,
) : Map<Int, List<SourceDistances>> {
    return neighboring(devices).alignedMap(euclideanDistance3D(position)) {
        _: Int, deviceValues: Map<Int, Float>, distance: Double ->
        deviceValues.entries.map { (to, distanceForMessaging) ->
            SourceDistances(
                to, 
                localId,
                distanceForMessaging,
                distance,
                senders.containsKey(to) &&
                distanceForMessaging != POSITIVE_INFINITY && 
                to != localId
            )
        }
    }.toMap()
    .filterKeys { senders.containsKey(it) && it != localId }
    .mapValues { (key, list) ->
        list.filter { it.isSourceValues && it.to == key}
    }
}
