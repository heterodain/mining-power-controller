# mining-power-controller

バッテリー残量に応じてPCの電源をON/OFFするプログラムです。  
(This program is turned on / off the PC power by battery remain capacity)

![SpringBoot](https://img.shields.io/badge/SpringBoot-2.5.3-green.svg) 
![Lombok](https://img.shields.io/badge/Lombok-1.18.20-green.svg) 
![j2mod](https://img.shields.io/badge/j2mod-2.7.0-green.svg)
![pi4j](https://img.shields.io/badge/pi4j-1.3-green.svg)

[![Video1](https://img.youtube.com/vi/rS7NZO7rW9k/0.jpg)](https://www.youtube.com/watch?v=rS7NZO7rW9k)
(using v1.0)

[![Video2](https://img.youtube.com/vi/DXjwRsGMiQ4/0.jpg)](https://www.youtube.com/watch?v=DXjwRsGMiQ4)
(using v1.1)

[![Video3](https://img.youtube.com/vi/WXjLHTAnlC4/0.jpg)](https://www.youtube.com/watch?v=WXjLHTAnlC4)
(using v1.2)

## 必要要件 (Requirement)

- PV コントローラー (PV controller)
  - Epever 製の Tracer AN/BN/CN シリーズ, LS-B シリーズ  
    (Epever Tracer AN/BN/CN Series, LS-B Series)
- Raspberry PI Zero W
- Java 8
  - Java 9 以降不可 (Java 9 or higher Not possible)
- Maven

## 使い方 (Usage)

1. Raspberry PI と PV コントローラーを RS485 USB アダプターで接続してください。  
   (Connect Raspberry PI and PV controller with RS485 USB Adapter)

2. application.yml を編集して、PV コントローラーとAmbientの接続情報を記入してください。  
   (Edit application.yml and fills connect information of PV Controller and Ambient)

3. コンパイル&パッケージング (Compile & Packaging)

    ```command
    mvn clean package
    ```

4. Raspberry PI上で実行 (Execute on Raspberry PI)

     ```command
     java -jar mining-powercontroller-1.0.jar
     ```
