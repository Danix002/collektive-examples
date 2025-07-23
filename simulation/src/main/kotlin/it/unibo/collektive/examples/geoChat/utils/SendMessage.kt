package it.unibo.collektive.examples.geoChat.utils

import it.unibo.collektive.aggregate.api.Aggregate
import it.unibo.collektive.stdlib.spreading.gradientCast
import it.unibo.collektive.stdlib.util.Point3D
import it.unibo.collektive.stdlib.util.euclideanDistance3D
import kotlin.Float.Companion.POSITIVE_INFINITY

/**
 * Spreads the intention to send a message within the aggregate network using a gradient-cast.
 *
 * The message propagation starts from nodes where [isSender] is true and spreads to neighbors
 * based on their spatial distance, limited by the given [distance].
 *
 * @param isSender Indicates if the current node is the message sender (source of the gradient).
 * @param deviceId The unique identifier of the device/node sending or forwarding the message.
 * @param distance The maximum distance within which the message should propagate.
 * @param position The 3D position of the current node used to compute Euclidean distance.
 * @param message The message content to be propagated.
 *
 * @return A Pair containing:
 *   - The originating sender's device ID.
 *   - A Pair of the accumulated distance from sender to this node and the propagated message string.
 *
 * Propagation stops beyond the specified [distance] by returning an infinite distance and an empty message.
 */
fun Aggregate<Int>.spreadIntentionToSendMessage(
    isSender: Boolean,
    deviceId: Int,
    distance: Float,
    position: Point3D,
    message: String
): Pair<Int, Pair<Float, String>> =
    gradientCast(
        source = isSender,
        local = deviceId to Pair(distance, message),
        metric = euclideanDistance3D(position),
        accumulateData = { fromSource, toNeighbor, dist ->
            if (fromSource + toNeighbor <= distance.toDouble()) {
                dist
            } else {
                deviceId to Pair(POSITIVE_INFINITY, "")
            }
        }
    )
