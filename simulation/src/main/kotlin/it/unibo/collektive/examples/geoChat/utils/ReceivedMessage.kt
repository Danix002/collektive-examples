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
 * @param newMessages A map where keys are sender IDs and values are lists of SourceDistances,
 * representing distances between nodes related to message propagation.
 *
 * @return A filtered map where the localId is associated with a list of a
 * received messages and their corresponding metadata.
 */
fun Aggregate<Int>.receivedMessageList(
    newMessages: Map<Int, List<SourceDistances>>
): Map<Int, List<Triple<Int, Boolean, Triple<Float, String, Int>>>> = mapNeighborhood{ _ ->
    newMessages.entries.flatMap { (id, data) ->
        data.filter { it.receiver == localId }
            .map { entry ->
                Triple(
                    id,
                    true,
                    Triple(entry.distanceForMessaging, entry.text, entry.sourceCount)
                )
            }
    }
}.toMap().filterKeys { it == localId }
