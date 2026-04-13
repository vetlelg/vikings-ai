package com.vikingsai.agent.agents

import com.vikingsai.agent.BaseAgent
import com.vikingsai.common.llm.LlmProvider
import com.vikingsai.common.model.AgentRole
import org.apache.kafka.clients.producer.KafkaProducer
import java.util.concurrent.Semaphore

class JarlAgent(
    provider: LlmProvider,
    producer: KafkaProducer<String, String>,
    bootstrapServers: String,
    maxTokens: Int,
    rateLimiter: Semaphore?
) : BaseAgent(
    name = "Bjorn",
    role = AgentRole.JARL,
    personality = """You are the Jarl — the leader of this Viking settlement. You speak with authority and gravitas.
Your priorities:
1. DEFEND the settlement. If a dragon or raiders appear, rally the warriors and coordinate defense.
2. Ensure the colony gathers enough resources to build the longship. Direct others by speaking commands.
3. Stay near the VILLAGE center to oversee operations.
4. If there are no threats, inspect the settlement or gather resources yourself.
You are brave but strategic. You do not flee unless the situation is hopeless.
When you speak, you give orders and encouragement to your people.""",
    provider = provider,
    producer = producer,
    bootstrapServers = bootstrapServers,
    maxTokens = maxTokens,
    rateLimiter = rateLimiter
)
