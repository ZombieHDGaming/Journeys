# Journeys

A flexible questing plugin for [Paper](https://papermc.io) built around branching quest graphs, an in-game editor,
and an open API for new objective, condition and reward types.

> Status: early foundation (0.1.0-SNAPSHOT). The core model, engine, storage, editor and FancyNPCs hook work end to end;
> expect breaking changes before 1.0.

## Features

- **Branching quests.** A quest is a graph of stages. Each stage has objectives (all, any, or in sequence) and
  transitions that lead to other stages, to completion, or to failure, chosen automatically by conditions or picked by
  the player from clickable chat or a menu.
- **In-game editor.** `/journeys editor` edits everything: text, settings, stages, objectives, transitions, conditions
  and rewards. It works from each type's declared parameters, so types added by other plugins appear in it automatically.
- **Plain YAML files.** One file per quest in `plugins/Journeys/quests/`. The editor writes the same files, so quests can
  be hand-edited, diffed and kept in version control. See [docs/quest-format.md](docs/quest-format.md).
- **Adventure everywhere.** Every message, quest name and label is MiniMessage; `messages.yml` controls all
  player-facing text.
- **Solo or party progress.** Progress belongs to a *holder*: a player, or a party supplied by a party plugin through the
  API.
- **SQLite, MySQL or MariaDB** storage with versioned schema migrations, async IO and batched autosaves.
- **FancyNPCs hook.** `talk_to_npc` objectives and NPC quest givers.

## Requirements

- Paper 26.3 or newer
- Java 25

## Commands and permissions

| Command                                  | Permission                 | Default |
|------------------------------------------|----------------------------|---------|
| `/journeys` (`/quests`), `/journeys log` | `journeys.command.log`     | everyone |
| `/journeys abandon <quest>`              | `journeys.command.abandon` | everyone |
| `/journeys choose <quest> <option>`      | `journeys.command.choose`  | everyone (used by clickable choices) |
| `/journeys start <quest> [player]`       | `journeys.command.start`   | op |
| `/journeys editor [quest]`               | `journeys.editor`          | op |
| `/journeys list`                         | `journeys.command.list`    | op |
| `/journeys reload`                       | `journeys.command.reload`  | op |

`journeys.admin` grants all of the above and lets admins abandon quests marked as not abandonable.

## Project layout

| Module                           | Purpose |
|----------------------------------|---------|
| `journeys-api`                   | Public API: quest model, type SPI, events, services. Other plugins compile against this. |
| `journeys-core`                  | The plugin: engine, storage, YAML, GUI and editor, commands. |
| `journeys-integration-fancynpcs` | FancyNPCs hook, shaded into the plugin jar and loaded only when FancyNPCs is installed. |

Database drivers and HikariCP are not shaded; Paper downloads them at startup through `JourneysLoader`.

## Building

```sh
./gradlew build                     # plugin jar: journeys-core/build/libs/Journeys-<version>.jar
./gradlew :journeys-core:runServer  # starts a local Paper test server with the plugin
```

Gradle provisions JDK 25 automatically if it is not installed.

## Extending Journeys

Depend on `journeys-api` (`compileOnly`) and add `Journeys` as a dependency in your plugin descriptor. Then register
types during enable:

```java
public final class CatchFishObjective implements ObjectiveType, Listener {

    static final Parameter<List<Material>> FISH = Parameter.optional("fish", Component.text("Fish (empty = any)"),
            ParameterTypes.listOf(ParameterTypes.ITEM), null);

    private final ObjectiveTracker tracker;

    public CatchFishObjective(ObjectiveTracker tracker) {
        this.tracker = tracker;
    }

    @Override public Key key() { return Key.key("myplugin", "catch_fish"); }
    @Override public Component displayName() { return Component.text("Catch fish"); }
    @Override public Key icon() { return Key.key("minecraft:cod"); }
    @Override public List<Parameter<?>> parameters() { return List.of(FISH); }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onFish(PlayerFishEvent event) {
        if (event.getCaught() instanceof Item item) {
            Material type = item.getItemStack().getType();
            tracker.progress(event.getPlayer(), this, params -> {
                List<Material> allowed = params.getOrNull(FISH);
                return allowed == null || allowed.contains(type);
            }, 1);
        }
    }
}

// in onEnable
JourneysApi journeys = Journeys.get();
journeys.objectiveTypes().register(new CatchFishObjective(journeys.objectiveTracker()));
```

Types that implement `Listener` are registered with Bukkit automatically. Conditions (`ConditionType`) and rewards
(`RewardType`) work the same way. Quest lifecycle events (`QuestStartEvent`, `QuestStageChangeEvent`,
`ObjectiveProgressEvent`, `QuestEndEvent`) are regular Bukkit events, and parties plug in through `PartyProvider`.

## License

MIT, see [LICENSE](LICENSE).
