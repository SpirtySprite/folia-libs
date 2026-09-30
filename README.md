# folia-libs

Libraries for plugins that run on [Paper](https://papermc.io) and [Folia](https://papermc.io/software/folia).
Each one is safe to call from any thread and works the same on both servers.

| Library | What it does | Docs |
|---|---|---|
| **FoliaBoard** | Packet-level scoreboards: sidebars, nametags, tab lists, below-name numbers, boss bars | [foliaboard/](foliaboard/README.md) |
| **FoliaGUI** | Inventory menus: paginated, searchable, anvil, sign, merchant, confirmations | [foliagui/](foliagui/README.md) |
| **FoliaNPC** | Packet-based NPCs with skins, nametags, equipment, movement and click actions | [folianpc/](folianpc/README.md) |
| **folia-commons** | The code they share: scheduling, text, version detection | [folia-commons/](folia-commons/README.md) |

None of them ships a `plugin.yml`. Depend on the ones you need and shade them into your plugin (relocate the
packages to your own namespace), or run them inside a library plugin of your own.

## Installing

The libraries are published through [JitPack](https://jitpack.io). Use the group
`com.github.SpirtySprite.folia-libs`, the module's artifact id, and the release tag as the version:

```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>

<dependencies>
    <dependency>
        <groupId>com.github.SpirtySprite.folia-libs</groupId>
        <artifactId>foliagui-api</artifactId>
        <version>foliagui-v1.0.0</version>
    </dependency>
</dependencies>
```

| Module | Artifact id |
|---|---|
| FoliaBoard | `foliaboard-core` |
| FoliaGUI | `foliagui-api` |
| FoliaNPC | `folianpc` |
| folia-commons | `folia-commons` |

Each library's README has the details, including the `plugin.yml` setting Folia needs
(`folia-supported: true`).

## Building

```
mvn verify
```

See [CONTRIBUTING.md](CONTRIBUTING.md) for the module layout, compatibility checks and how releases work.

## License

MIT. See [LICENSE](LICENSE).
