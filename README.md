# SupaBillford

A configurable Billford trade GUI for Paper/Purpur 1.21.11, built with Java 21.

## Features

- `/billford` opens the trade GUI.
- `/billford reload` reloads configuration (requires `supabillford.admin`).
- `/setbillforditem <key>` saves the item in your hand to `trade-items.<key>` in config.
- Configure any number of required items and the reward in `config.yml`.
- Configure GUI slots, item names/lore, enchantments, flags, custom model data, messages, and sounds.
- Supports legacy color codes and hex formats such as `<##A855F7>`.
- Optionally require matching item metadata with `trade.match-item-meta: true`.

## Build

Requires JDK 21 and Maven 3.9+.

```sh
mvn clean verify
```

The plugin JAR is created at `target/SupaBillford.jar`. GitHub Actions also uploads it as a workflow artifact.

## Install

Put `SupaBillford.jar` into your server's `plugins` folder, start the server once to generate the configuration, then edit `plugins/SupaBillford/config.yml` and run `/billford reload`.

## Item configuration

Add or remove entries under `trade.required-items`. Each item supports `material`, `amount`, `name`, `lore`, `enchantments`, `custom-model-data`, and `flags`. The reward uses the same item fields under `trade.reward`.

```yaml
trade:
  match-item-meta: false
  required-items:
    - material: DIAMOND
      amount: 1
    - material: OBSIDIAN
      amount: 8
  reward:
    material: RESPAWN_ANCHOR
    amount: 1
```

When `match-item-meta` is false, required items match by material type. When true, configured item metadata must also match. Registered custom materials can be used when exposed through Bukkit's Material API.
