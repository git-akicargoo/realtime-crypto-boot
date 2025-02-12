#!/bin/bash

./gradlew test --tests "com.example.boot.exchange.layer1_core.protocol.binance.BinanceProtocolImplTest" --info

./gradlew test --tests "com.example.boot.exchange.layer1_core.protocol.bithumb.BithumbProtocolImplTest" --info

./gradlew test --tests "com.example.boot.exchange.layer1_core.protocol.upbit.UpbitProtocolImplTest" --info

./gradlew test --tests "com.example.boot.exchange.layer2_websocket.connection.BinanceMarketTest" --info

./gradlew test --tests "com.example.boot.exchange.layer2_websocket.connection.BinanceWebSocketTest" --info

./gradlew test --tests "com.example.boot.exchange.layer2_websocket.connection.BithumbWebSocketTest" --info

./gradlew test --tests "com.example.boot.exchange.layer2_websocket.connection.UpbitWebSocketTest" --info

./gradlew test --tests "com.example.boot.exchange.layer3_data_converter.integration.BinanceConverterIntegrationTest" --info

./gradlew test --tests "com.example.boot.exchange.layer3_data_converter.integration.UpbitConverterIntegrationTest" --info

./gradlew test --tests "com.example.boot.exchange.layer3_data_converter.integration.BithumbConverterIntegrationTest" --info

./gradlew test --tests "com.example.boot.exchange.layer3_data_converter.service.ExchangeDataIntegrationServiceImplTest" --info

./gradlew test --tests "com.example.boot.exchange.layer3_data_converter.service.ExchangeDataIntegrationServiceIntegrationTest" --info