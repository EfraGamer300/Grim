# WolfAC — WolfNetwork Edition

Esta versão do plugin (`EfraGamer300/Grim`) é feita **exclusivamente para os servidores da WolfNetwork**.

## O que mudou vs upstream

- Nome do plugin Bukkit: `GrimAC` → `WolfAC`
- Autor: `GrimAC` → `WolfNetwork`
- Prefix padrão das mensagens: `&bGrim &8»` → `&bWolfNetwork &8»`
- `config/en.yml` e `config/pt.yml` com cabeçalho WolfNetwork + `prefix` padrão
- `README.md` com aviso de exclusividade
- Funcionalidade anticheat: idêntica ao upstream `GrimAnticheat/Grim` branch `2.0` (Folia/Paper 1.8–26.2)

## Aviso legal

- Baseado em **GrimAC** (`https://github.com/GrimAnticheat/Grim`), licenciado em **GPLv3**. Arquivo `LICENSE` original mantido.
- Créditos do motor anticheat pertencem aos autores do GrimAnticheat e contribuidores.
- Este fork **não tem suporte oficial** do GrimAnticheat (Discord/Wiki/issues upstream). Suporte apenas via equipe WolfNetwork.
- Uso pretendido: apenas servidores da WolfNetwork. Redistribuição deve respeitar a GPLv3 (fonte disponível, mesmos direitos).
- Comandos/permissões internas (`/grim`, `grim.*`) foram mantidos para compatibilidade.

## Build

```bash
./gradlew :bukkit:shadowJar -x test
# jar em bukkit/build/libs (funciona em Paper e Folia)
```

## Links

- Fork: https://github.com/EfraGamer300/Grim
- Upstream: https://github.com/GrimAnticheat/Grim
