package it.unibo.collektive.examples.geoChat

import it.unibo.alchemist.collektive.device.CollektiveDevice
import it.unibo.collektive.aggregate.api.Aggregate
import it.unibo.collektive.stdlib.util.Point3D
import it.unibo.alchemist.model.Environment
import it.unibo.alchemist.model.Node
import it.unibo.alchemist.model.Position
import it.unibo.collektive.examples.geoChat.utils.getListOfDevicesValues
import it.unibo.collektive.examples.geoChat.utils.saveNewMessage
import it.unibo.collektive.examples.geoChat.utils.receivedMessageList
import it.unibo.collektive.examples.geoChat.utils.spreadIntentionToSendMessage
import kotlin.Float.Companion.POSITIVE_INFINITY
import kotlin.random.Random

/**
 * Generates a random 3D point near the position of a given node within an environment.
 *
 * This function takes the 2D coordinates (x, y) of the node's current position and
 * adds a random offset between -5.0 and 5.0 to both x and y, while setting the z coordinate to 0.0.
 * The result is a new Point3D located within a random radius around the original position,
 * useful for simulating spatial variability or movement in a 3D space where the z-axis is fixed.
 *
 * @param environment The environment containing the nodes and their positions.
 * @param node The node for which the random nearby position is generated.
 *
 * @return A Point3D representing a random position near the node's original coordinates.
 */
fun <P : Position<P>> generateRandomPoint3D(
    environment: Environment<Any?, P>,
    node: Node<Any?>
): Point3D {
    val (x, y) = environment.getPosition(node).coordinates
    val z = 0.0
    return Point3D(
        coordinates = Triple(
            x + Random.nextDouble(-5.0, 5.0),
            y + Random.nextDouble(-5.0, 5.0),
            z
        )
    )
}

/**
 * Randomly determines whether the current node should act as a message source.
 *
 * Each call has a 25% chance of returning true, meaning the node will initiate
 * message propagation in the current round.
 *
 * @return True if the node is selected as a source; false otherwise.
 */
fun isSource() = Random.nextFloat() < 0.25f

/**
 * Entry point for a geo-aware chat simulation in the aggregate network.
 *
 * Each device determines whether it is a message source, generates a random position,
 * and sets an initial message and propagation distance if it is a source.
 * Then, it spreads the intention to send a message through the network using
 * the [spreadIntentionToSendMessage] gradient-based method.
 *
 * The function collects messages from senders within range, saves new messages,
 * and filters the received messages to determine which ones should be accepted
 * by the current device.
 *
 * @param simulatedDevice The device running this aggregate program, containing its environment and node info.
 *
 * @return A map of device IDs to pairs of accumulated distance and message string,
 * filtered to only include devices from which messages are actually received.
 */
fun Aggregate<Int>.geoChatEntrypoint(
    simulatedDevice: CollektiveDevice<*>,
): Map<Int, Pair<Float, String>> {
    //============ SETUP
    var distance = POSITIVE_INFINITY; var message = ""; var senders = emptyMap<Int, Pair<Float, String>>()
    val position = generateRandomPoint3D(simulatedDevice.environment, simulatedDevice.node)
    val isSource = isSource()
    if (isSource) {
        simulatedDevice["isSource"] = true
        distance = Random.nextInt(5, 20).toFloat()
        message = "Hello! I'm device $localId"
    }else{
        simulatedDevice["isSource"] = false
    }

    //============ SEND MESSAGE
    val sender = spreadIntentionToSendMessage(
        isSender = isSource,
        deviceId = localId,
        distance = distance,
        position = position,
        message = message
    )

    //============ RECEIVE AND SAVE MESSAGE
    if(sender.second.first != POSITIVE_INFINITY){
        val tmp = senders.toMutableMap()
        tmp.put(sender.first, sender.second)
        senders = tmp
    }
    val newMessage = saveNewMessage(getListOfDevicesValues(senders), position, senders)

    //============ VISUALIZE MESSAGE
    val messagesToReceive = receivedMessageList(newMessage)
    return senders.filterKeys {
        messagesToReceive.values.flatten().contains(it to true)
    }
}
