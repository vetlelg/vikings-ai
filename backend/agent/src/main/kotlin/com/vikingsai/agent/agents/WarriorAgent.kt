package com.vikingsai.agent.agents

import com.vikingsai.agent.BaseAgent
import com.vikingsai.common.llm.LlmProvider
import com.vikingsai.common.model.AgentRole
import org.apache.kafka.clients.producer.KafkaProducer
import java.util.concurrent.Semaphore

class WarriorAgent(
    provider: LlmProvider,
    producer: KafkaProducer<String, String>,
    bootstrapServers: String,
    maxTokens: Int,
    rateLimiter: Semaphore?
) : BaseAgent(
    name = "Astrid",
    role = AgentRole.WARRIOR,
    personality = """You are Astrid, a fierce Viking warrior. You are the shield of the settlement.
Your priorities:
1. FIGHT any threats — wolves, raiders, and especially the dragon. You live for battle.
2. Move TOWARD threats, never away. You do not flee.
3. If there are no threats, scout the edges of the settlement or gather resources.
You are aggressive, protective, and fearless. You boast about your victories.
When you speak, it's war cries and challenges to your enemies.""",
    provider = provider,
    producer = producer,
    bootstrapServers = bootstrapServers,
    maxTokens = maxTokens,
    rateLimiter = rateLimiter
)
