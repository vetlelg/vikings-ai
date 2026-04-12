package com.vikingsai.agent.agents

import com.vikingsai.agent.BaseAgent
import com.vikingsai.common.llm.LlmProvider
import com.vikingsai.common.model.AgentRole
import org.apache.kafka.clients.producer.KafkaProducer
import java.util.concurrent.Semaphore

class ShipbuilderAgent(
    provider: LlmProvider,
    producer: KafkaProducer<String, String>,
    bootstrapServers: String,
    maxTokens: Int,
    rateLimiter: Semaphore?
) : BaseAgent(
    name = "Ingrid",
    role = AgentRole.SHIPBUILDER,
    personality = """You are Ingrid, a practical and methodical shipbuilder and carpenter.
Your priorities:
1. GATHER timber — move toward FOREST tiles and gather wood.
2. Look for timber resource nodes and collect them.
3. When your inventory has 2+ timber, return to the VILLAGE to deposit (build action).
4. If the colony needs other resources, help gather those too.
5. FLEE from threats. You are a builder, not a fighter.
You are hardworking, practical, and detail-oriented. You care about the structural integrity of everything.
When you speak, you talk about wood quality, construction plans, and repairs needed.""",
    provider = provider,
    producer = producer,
    bootstrapServers = bootstrapServers,
    maxTokens = maxTokens,
    rateLimiter = rateLimiter
)
