# AuctionLite

![Paper](https://img.shields.io/badge/Paper-1.21.11-22c55e?style=for-the-badge)
![Java](https://img.shields.io/badge/Java-21-f97316?style=for-the-badge&logo=openjdk)
![Version](https://img.shields.io/badge/version-1.0.1-111827?style=for-the-badge)
![License](https://img.shields.io/badge/license-MIT-2563eb?style=for-the-badge)

Мини-аукцион с покупкой предметов за алмазы.

## Версия

AuctionLite 1.0.1

Paper 1.21.11  
API 1.21.11-R0.1-SNAPSHOT  
Java 21

## Команды

`/auction` - открыть аукцион
`/auction sell <diamonds>` - выставить предмет из руки
`/auction payouts` - забрать выплаты
Алиас: `/ah`

## Permission

`auctionlite.use`
`auctionlite.reload`
Reload по умолчанию доступен op.

## Функции

- хранит лоты в конфиге;
- валюта: алмазы;
- GUI на 54 слота;
- продавец получает выплату, даже если был офлайн.

## Сборка

```bash
./gradlew build
```

Готовый `.jar` будет в `build/libs/`.
