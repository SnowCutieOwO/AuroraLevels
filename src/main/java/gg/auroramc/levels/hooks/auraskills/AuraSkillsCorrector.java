package gg.auroramc.levels.hooks.auraskills;

import com.google.common.collect.Maps;
import dev.aurelium.auraskills.api.AuraSkillsApi;
import dev.aurelium.auraskills.api.stat.Stat;
import dev.aurelium.auraskills.api.stat.StatModifier;
import gg.auroramc.levels.AuroraLevels;
import gg.auroramc.levels.api.leveler.Leveler;
import gg.auroramc.levels.api.reward.RewardCorrector;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class AuraSkillsCorrector implements RewardCorrector {
    private final AuroraLevels plugin;

    public AuraSkillsCorrector(AuroraLevels plugin) {
        this.plugin = plugin;
    }

    @Override
    public void correctRewards(Leveler leveler, Player player) {
        CompletableFuture.runAsync(() -> {
            var data = leveler.getUserData(player);
            var level = data.getLevel();

            Map<Stat, Double> statMap = Maps.newHashMap();

            // Reset all stat modifiers first
            for (var stat : AuraSkillsApi.get().getGlobalRegistry().getStats()) {
                statMap.put(stat, 0.0);
            }

            // Gather new stat modifiers
            for (long i = 1; i < level + 1; i++) {
                var matcher = leveler.getLevelMatcher().getBestMatcher(i);
                if (matcher == null) continue;
                var formulaPlaceholders = leveler.getRewardFormulaPlaceholders(player, i);
                for (var reward : matcher.rewards()) {
                    if (reward instanceof AuraSkillsStatReward statReward) {
                        statMap.merge(statReward.getStat(), statReward.getValue(formulaPlaceholders), Double::sum);
                    }
                }
            }

            // I'm not sure if it's safe to call AuraSkills apis async, so lets just run it on the main thread
            Bukkit.getGlobalRegionScheduler().run(plugin, (task) -> {
                // Apply the new stat modifiers
                for (var entry : statMap.entrySet()) {
                    var statKey = AuraSkillsStatReward.getAURA_SKILLS_STAT() + entry.getKey().getId().toString();
                    var user = AuraSkillsApi.get().getUser(player.getUniqueId());

                    var oldModifier = user.getStatModifier(statKey);

                    if (oldModifier == null) {
                        if (entry.getValue() > 0) {
                            user.addStatModifier(new StatModifier(statKey, entry.getKey(), entry.getValue()));
                        }
                    } else if (entry.getValue() <= 0) {
                        user.removeStatModifier(statKey);
                    } else if (entry.getValue() != oldModifier.value()) {
                        user.addStatModifier(new StatModifier(statKey, entry.getKey(), entry.getValue()));
                    }
                }
            });
        });
    }
}
