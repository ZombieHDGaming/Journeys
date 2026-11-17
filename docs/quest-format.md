# Quest file format

Each quest lives in `plugins/Journeys/quests/<id>.yml`. The id is the file name and may only contain lowercase
letters, digits, `_` and `-`. The in-game editor (`/journeys editor`) reads and writes these same files, so you can mix
GUI and hand editing. After editing by hand, run `/journeys reload`.

All player-facing text is [MiniMessage](https://docs.advntr.dev/minimessage/format.html).

See [`example_woodcutter.yml`](../journeys-core/src/main/resources/quests/example_woodcutter.yml) for a complete,
working example with a player choice and a conditional branch.

## Top level

| Key            | Default          | Meaning                                                                 |
|----------------|------------------|-------------------------------------------------------------------------|
| `display-name` | the id           | Quest name.                                                             |
| `description`  | `[]`             | Lines shown in menus.                                                   |
| `icon`         | `minecraft:book` | Item used in menus.                                                     |
| `settings`     | see below        |                                                                         |
| `start-stage`  | first stage      | Stage the quest begins in.                                              |
| `requirements` | `[]`             | [Conditions](#conditions) the player must pass to start.               |
| `rewards`      | `[]`             | [Rewards](#rewards) granted on completion.                              |
| `properties`   | `{}`             | Free-form data for integrations, e.g. `fancynpcs: { givers: bob }`.     |
| `stages`       | required         | Map of stage id to [stage](#stages).                                    |

### settings

| Key               | Default | Meaning                                                                          |
|-------------------|---------|----------------------------------------------------------------------------------|
| `repeatable`      | `false` | Can be started again after completion.                                           |
| `cooldown`        | `0s`    | Wait between completion and restart, e.g. `30m`, `1d12h`.                        |
| `max-completions` | `0`     | Total completions allowed; `0` is unlimited.                                     |
| `scope`           | `solo`  | `solo`, or `party` to share progress with the player's party (needs a provider). |
| `abandonable`     | `true`  | Players may abandon it themselves.                                               |
| `hidden`          | `false` | Not shown in the quest log.                                                      |
| `permission`      | `false` | Also require the permission `journeys.quest.<id>` to start.                     |
| `board`           |         | See [Quest board](#quest-board).                                                 |

### Quest board

Players browse quests with `/journeys board` (or the button in their quest log). Each quest's `settings.board` controls
how it appears there:

| Key                | Default   | Meaning                                                                                       |
|--------------------|-----------|-----------------------------------------------------------------------------------------------|
| `category`         | `general` | Category id. Names, icons and order come from `categories.yml`.                               |
| `mode`             | `start`   | `hidden` (not listed), `view` (listed, started elsewhere) or `start` (click to accept).      |
| `show-when-locked` | `true`    | Show the quest with a ✔/✖ checklist of its requirements while locked, or hide it until unlocked. |

The board opens on a category list when quests span more than one category. `/journeys board <category>` opens one
category directly. To make an NPC open a board, give it a FancyNPCs action:
`/npc action <npc> interact add player_command journeys board <category>`.

## Stages

A quest is a graph of stages. When a stage's objectives are done, its rewards are granted and a transition is followed.

| Key              | Default     | Meaning                                                                                       |
|------------------|-------------|-----------------------------------------------------------------------------------------------|
| `display-name`   | stage id    |                                                                                               |
| `description`    | `[]`        | Sent to players when they enter the stage.                                                    |
| `objective-mode` | `all`       | `all` (any order), `any` (one is enough) or `sequential` (in listed order).                  |
| `objectives`     | `[]`        | A stage without objectives completes as soon as it is entered.                                |
| `branch-mode`    | `automatic` | `automatic`: first transition whose conditions pass. `choice`: the player picks one.          |
| `transitions`    | `[]`        | No transitions means the quest completes after this stage.                                    |
| `rewards`        | `[]`        | Granted when the stage's objectives are done.                                                 |

### Objectives

```yaml
objectives:
  - id: logs                    # unique within the stage; progress is stored under it
    type: journeys:break_block  # namespace may be omitted for built-in types
    amount: 16
    description: '<gray>Chop logs'   # optional, replaces the generated text
    params:
      block: [oak_log, birch_log]    # lists are allowed wherever a type takes several values
```

### Transitions

```yaml
transitions:
  - to: deliver                 # a stage id, '@complete' or '@fail'
    label: '<green>Deliver it'  # shown on choice stages
    conditions:                 # all must pass for the transition to be available
      - type: journeys:min_level
        params: { level: 5 }
```

On an `automatic` stage, transitions are tried top to bottom. If none is available the quest waits on that stage and
re-checks periodically, so you can gate progress on things like permissions.

## Built-in types

### Objectives

| Type                     | Parameters                                          |
|--------------------------|-----------------------------------------------------|
| `journeys:kill_entity`   | `entity`: entity types, empty for any               |
| `journeys:break_block`   | `block`: blocks, empty for any; `count-placed` (false) |
| `journeys:place_block`   | `block`: blocks, empty for any; `subtract-on-break` (true) |
| `journeys:collect_item`  | `item`: items picked up, empty for any; `count-dropped` (false) |
| `journeys:craft_item`    | `item`: items crafted, empty for any                |
| `journeys:fish`          | `item`: items caught, empty for any                 |
| `journeys:reach_location`| `world`, `x`, `y`, `z`, `radius` (3), `name`        |
| `journeys:talk_to_npc`   | `npc`: FancyNPCs NPC names (only with FancyNPCs)    |

#### Anti-farming

Journeys remembers which blocks players placed (stored in the world's chunk data, so it survives restarts and follows
blocks moved by pistons). By default:

- **break_block** ignores blocks placed by players, so placing and breaking the same block gains nothing. Fully grown
  crops still count even though the player planted them, and trees grown from player-planted saplings count as
  natural. Set `count-placed: true` for quests where breaking placed blocks is the point.
- **place_block** counts placements *net of removals*: breaking a block you placed takes the progress back, as long as
  the stage is still in progress. Set `subtract-on-break: false` to disable this.
- **collect_item** ignores items thrown by any player and items dropped by breaking player-placed blocks. Set
  `count-dropped: true` to count them.

Blocks placed before Journeys was installed are not known and count as natural.

### Conditions

Every condition accepts `negate: true` to invert it, and an optional `description` (MiniMessage) that players see on
the quest board's requirement checklist instead of the generated text:

```yaml
requirements:
  - type: journeys:permission
    description: '<gray>Reach the <gold>Miner</gold> rank'
    params: { permission: ranks.miner }
```

Quest-progress conditions check every holder the player belongs to, so solo progress counts towards a party quest's
requirements and the other way round.

| Type                       | Parameters                                                                                  |
|----------------------------|---------------------------------------------------------------------------------------------|
| `journeys:permission`      | `permission`                                                                                |
| `journeys:quest_completed` | `quest`, `times` (1)                                                                        |
| `journeys:completed_count` | `amount`, `category` (any), `count-repeats` (false): completed N different quests          |
| `journeys:quest_stage`     | `quest`, `stage`, `current-only` (false): reached that stage in the current or latest run   |
| `journeys:quest_branch`    | `quest`, `stage`, `choice`: in the current or latest run, left `stage` towards `choice` (a stage id, `@complete` or `@fail`) |
| `journeys:min_level`       | `level`                                                                                     |

See [`example_lumber_baron.yml`](../journeys-core/src/main/resources/quests/example_lumber_baron.yml) for a quest that
unlocks only after a particular branch of another quest.

### Rewards

| Type                  | Parameters                                                  |
|-----------------------|-------------------------------------------------------------|
| `journeys:command`    | `command`, run from console; `{player}` is replaced         |
| `journeys:experience` | `amount`, `levels` (false)                                  |
| `journeys:item`       | `item`, `amount` (1), `name` (MiniMessage)                  |
| `journeys:message`    | `message` (MiniMessage, `<player>` available)               |

## Integration properties

| Property           | Integration | Meaning                                                                     |
|--------------------|-------------|-----------------------------------------------------------------------------|
| `fancynpcs.givers` | FancyNPCs   | Comma-separated NPC names. Interacting with one offers (starts) the quest.   |
