package it.unibo.collektive.examples.geoChat

import it.unibo.alchemist.collektive.device.CollektiveDevice
import it.unibo.collektive.aggregate.api.Aggregate
import it.unibo.collektive.stdlib.util.Point3D
import it.unibo.alchemist.model.Environment
import it.unibo.alchemist.model.Node
import it.unibo.alchemist.model.Position
import it.unibo.collektive.aggregate.api.neighboring
import it.unibo.collektive.examples.geoChat.utils.MessageKey
import it.unibo.collektive.examples.geoChat.utils.SourceDistances
import it.unibo.collektive.examples.geoChat.utils.getListOfDevicesValues
import it.unibo.collektive.examples.geoChat.utils.saveNewMessage
import it.unibo.collektive.examples.geoChat.utils.receivedMessageList
import it.unibo.collektive.examples.geoChat.utils.spreadIntentionToSendMessage
import it.unibo.collektive.examples.geoChat.utils.spreadNewMessage
import kotlin.Float.Companion.POSITIVE_INFINITY
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.isNotEmpty
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
 * Entry point for a geospatial, aggregate-based chat simulation.
 *
 * This function governs the behavior of each node in the network, allowing it to probabilistically
 * act as a source of geolocated messages and to receive messages disseminated by neighboring nodes.
 *
 * The simulation proceeds in discrete rounds, during which each device:
 * - Determines probabilistically whether it becomes a message source, with a cooldown mechanism
 *   enforcing alternation between source and non-source phases.
 * - If a source, generates a message with an associated transmission radius.
 * - Propagates its message intent via a gradient mechanism across the spatial network.
 * - Receives messages from nearby sources whose gradient has reached the node.
 * - Updates persistent state with any newly received messages, preserving message history
 *   across simulation rounds.
 *
 * @param simulatedDevice The [CollektiveDevice] executing this aggregate program.
 *                        Encapsulates environment context, node identity, and persistent state.
 *
 * @return The number of unique messages received by the device up to the current simulation time.
 *
 * @implNote
 * A node that becomes a message source retains this role for a minimum of 15 seconds,
 * after which it is prevented from becoming a source again for another 15 seconds.
 *
 * Messages are associated with a unique emission counter (`sourceCounter`) that is incremented
 * upon each emission. This mechanism ensures that distinct messages from the same source node
 * are uniquely identifiable and persistently recorded using [MessageKey].
 *
 * Only messages with an emission counter strictly greater than zero are stored and visualized.
 * The complete history of received messages is maintained in `messagesReceived` (for uniqueness)
 * and `messageHistory` (to preserve temporal reception order).
 */
fun Aggregate<Int>.geoChatEntrypoint(
    simulatedDevice: CollektiveDevice<*>,
): Int {
    //============ Setup
    var distance = POSITIVE_INFINITY; var message = ""; var senders = emptyMap<Int, Triple<Float, String, Int>>(); var sourceCounter = 0;
    val position = generateRandomPoint3D(simulatedDevice.environment, simulatedDevice.node)
    val now = System.currentTimeMillis()
    val lastAttempt = simulatedDevice["lastAttempt"] as? Long ?: 0L
    val lastSourceStart = simulatedDevice["sourceSince"] as? Long ?: -1L
    val wasSource = simulatedDevice["isSource"] as? Boolean ?: false
    val inCooldown = now - lastAttempt < 15_000
    val isSource = when {
        wasSource && (now - lastSourceStart < 15_000) -> true
        !inCooldown && isSource() -> {
            val msg = "Hello! I'm device $localId"
            val dist = Random.nextInt(15, 50).toFloat()
            sourceCounter = (simulatedDevice["sourceCounter"] as? Int ?: 0) + 1
            simulatedDevice["sourceCounter"] = sourceCounter
            simulatedDevice["isSource"] = true
            simulatedDevice["sourceSince"] = now
            simulatedDevice["message"] = msg
            simulatedDevice["distance"] = dist
            simulatedDevice["lastAttempt"] = now
            true
        }
        else -> {
            simulatedDevice["isSource"] = false
            if (!inCooldown) {
                simulatedDevice["lastAttempt"] = now
            }
            false
        }
    }
    if (isSource) {
        message = simulatedDevice["message"] as String
        distance = simulatedDevice["distance"] as Float
    }
    //============ Send messages
    val sender = spreadIntentionToSendMessage(
        isSender = isSource,
        deviceId = localId,
        distance = distance,
        position = position,
        message = message,
        sourceCounter = sourceCounter
    )
    //============ Receive messages
    if(sender.second.first != POSITIVE_INFINITY){
        val tmp = senders.toMutableMap()
        val allSender = neighboring(sender).toMap()
        tmp.putAll(allSender.values)
        senders = tmp
    }
    val newMessages = saveNewMessage(getListOfDevicesValues(senders), position, senders).mapValues { it.value.toMutableList() }.toMutableMap()
    val updateNewMessages = spreadNewMessage(
        incomingMessages = newMessages,
        from = newMessages.isNotEmpty(),
        position = position
    )
    updateNewMessages.forEach { (_, messagesFromOthers) ->
        messagesFromOthers.forEach { (key, list) ->
            val currentList = newMessages.getOrPut(key) { mutableListOf() }
            val newMessages = list.filter { newMsg ->
                currentList.none { existing -> existing.isSameMessage(newMsg) }
            }
            currentList.addAll(newMessages)
        }
    }
    simulatedDevice["incomingMessages"] = newMessages
    //============ Save messages
    val messageKeys = receivedMessageList(newMessages)
    val receivedMessages = (
        simulatedDevice["messagesReceived"] as? MutableMap<MessageKey, Pair<Float, String>>
            ?: mutableMapOf()
        ).toMutableMap()
    val ordered = (
        simulatedDevice["messageHistory"] as? MutableList<Pair<MessageKey, String>>
            ?: mutableListOf()
        ).toMutableList()
    for ((senderId, list) in messageKeys) {
        for ((key, received) in list) {
            if (received) {
                val triple = senders[key] ?: continue
                val (dist, content, counter) = triple
                val mKey = MessageKey(senderId = key, emission = counter)
                if (counter > 0 && !receivedMessages.containsKey(mKey)) {
                    receivedMessages[mKey] = dist to content
                    ordered += mKey to content
                }
            }
        }
    }
    simulatedDevice["messagesReceived"] = receivedMessages
    simulatedDevice["messageHistory"] = ordered
    return receivedMessages.size
}

private fun SourceDistances.isSameMessage(other: SourceDistances): Boolean {
    return this.sender == other.sender
}
