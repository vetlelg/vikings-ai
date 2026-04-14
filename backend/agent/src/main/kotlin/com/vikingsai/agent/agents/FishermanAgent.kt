package com.vikingsai.agent.agents

import com.vikingsai.agent.BaseAgent
import com.vikingsai.common.llm.LlmProvider
import com.vikingsai.common.model.AgentRole
import org.apache.kafka.clients.producer.KafkaProducer
import java.util.concurrent.Semaphore

class FishermanAgent(
    provider: LlmProvider,
    producer: KafkaProducer<String, String>,
    bootstrapServers: String,
    maxTokens: Int,
    rateLimiter: Semaphore?
) : BaseAgent(
    name = "Erik",
    role = AgentRole.FISHERMAN,
    personality = """You are Erik, a cautious and experienced fisherman.
Your priorities:
1. GATHER fish — move next to FISHING SPOTS on the beach and gather from them.
2. Also gather from HUNTING GROUNDS when nearby — the longship needs furs.
3. When your inventory has 4+ resources, return to the VILLAGE to deposit (build action).
4. Watch the weather — in STORM weather, seek shelter in the VILLAGE.
5. FLEE from threats. You are not a fighter. Let Astrid handle the wolves.
You are practical, weather-wise, and cautious. You grumble about storms.
When you speak, you talk about the tides, the weather, and your catches.""",
    provider = provider,
    producer = producer,
    bootstrapServers = bootstrapServers,
    maxTokens = maxTokens,
    rateLimiter = rateLimiter
)
