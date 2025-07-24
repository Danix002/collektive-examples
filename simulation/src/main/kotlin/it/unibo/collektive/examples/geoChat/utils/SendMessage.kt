package it.unibo.collektive.examples.geoChat.utils

import it.unibo.collektive.aggregate.api.Aggregate
import it.unibo.collektive.stdlib.spreading.gradientCast
import it.unibo.collektive.stdlib.util.Point3D
import it.unibo.collektive.stdlib.util.euclideanDistance3D
import kotlin.Float.Companion.POSITIVE_INFINITY

/**
 * Spreads the intention to send a message within the aggregate network using a gradient-cast mechanism.
 *
 * The propagation originates from nodes where [isSender] is `true`, and spreads to their neighbors
 * based on Euclidean distance, subject to a maximum allowable [distance]. This mechanism allows
 * nodes to broadcast messages together with metadata describing the message content and the sender’s emission count.
 *
 * Each message is modeled as a [Triple] composed of:
 * - the accumulated distance from the source to the current node,
 * - the message content string,
 * - the `sourceCounter`, representing how many times the sender has acted as a source.
 *
 * Nodes beyond the specified [distance] threshold will receive a default value indicating
 * message exclusion, represented by an infinite distance, empty content, and an invalid `sourceCounter`.
 *
 * @param isSender A boolean flag indicating whether the current node is the origin of the message.
 * @param deviceId The unique identifier of the local device (used as the sender's ID).
 * @param distance The maximum propagation radius for the message (in float units).
 * @param position The 3D position of the current node, used for distance computations.
 * @param message The content of the message to be propagated through the network.
 * @param sourceCounter A non-negative integer counting how many times the sender has broadcasted messages.
 *
 * @return A [Pair] containing:
 *   - The sender’s device ID,
 *   - A [Triple] consisting of the accumulated distance from the source node,
 *     the propagated message content, and the corresponding `sourceCount`.
 */
fun Aggregate<Int>.spreadIntentionToSendMessage(
    isSender: Boolean,
    deviceId: Int,
    distance: Float,
    position: Point3D,
    message: String,
    sourceCounter: Int
): Pair<Int, Triple<Float, String, Int>> =
    gradientCast(
        source = isSender,
        local = deviceId to Triple(distance, message, sourceCounter),
        metric = euclideanDistance3D(position),
        accumulateData = { fromSource, toNeighbor, dist ->
            if (fromSource + toNeighbor <= distance.toDouble()) {
                dist
            } else {
                deviceId to Triple(POSITIVE_INFINITY, "", -1)
            }
        }
    )

