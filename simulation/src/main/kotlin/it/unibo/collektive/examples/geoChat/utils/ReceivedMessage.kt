package it.unibo.collektive.examples.geoChat.utils

import it.unibo.collektive.aggregate.api.Aggregate
import it.unibo.collektive.aggregate.api.mapNeighborhood

data class MessageKey(val senderId: Int, val emission: Int)

/**
 * Processes a map of senders and their distances to produce a list of messages
 * that the current node has received or should consider.
 *
 * For each neighbor in the network, it checks if there is a relevant entry
 * indicating a message transmission from the current node (`localId`) to the sender.
 * It then evaluates whether the message should be considered received based on
 * comparing `distanceForMessaging` with the actual `distance`.
 *
 * The result is filtered to only include entries relevant to the current node.
 *
 * @param senders A map where keys are sender IDs and values are lists of SourceDistances,
 * representing distances between nodes related to message propagation.
 *
 * @return A map from device IDs to lists of pairs, each containing a sender ID and
 * a Boolean indicating whether the message was effectively received (true if
 * distanceForMessaging >= distance).
 */
fun Aggregate<Int>.receivedMessageList(
    senders: Map<Int, List<SourceDistances>>
): Map<Int, List<Pair<Int, Boolean>>> = mapNeighborhood{ _ ->
    senders.entries.mapNotNull { (id, distance) ->
        val entry = distance.find { it.sender == id && it.receiver == localId }
        entry?.let { id to true }
    }
}.toMap().filterKeys { it == localId }
