# mining-power-controller

バッテリー残量に応じてPCの電源をON/OFFするプログラムです。  
(This program is turned on / off the PC power by battery remain capacity)

![SpringBoot](https://img.shields.io/badge/SpringBoot-2.5.6-green.svg) 
![Lombok](https://img.shields.io/badge/Lombok-1.18.20-green.svg) 
![j2mod](https://img.shields.io/badge/j2mod-2.7.3-green.svg)
![pi4j](https://img.shields.io/badge/pi4j-1.3-green.svg)

[![Video1](https://img.youtube.com/vi/rS7NZO7rW9k/0.jpg)](https://www.youtube.com/watch?v=rS7NZO7rW9k)
(use v1.0)

[![Video2](https://img.youtube.com/vi/DXjwRsGMiQ4/0.jpg)](https://www.youtube.com/watch?v=DXjwRsGMiQ4)
(use v1.1)

[![Video3](https://img.youtube.com/vi/WXjLHTAnlC4/0.jpg)](https://www.youtube.com/watch?v=WXjLHTAnlC4)
(use v1.2)

[![Video](https://img.youtube.com/vi/li6INqr9ar8/0.jpg)](https://www.youtube.com/watch?v=li6INqr9ar8)
(use v1.3)

[![Video](https://img.youtube.com/vi/_N5hQMTwDSY/0.jpg)](https://www.youtube.com/watch?v=_N5hQMTwDSY)
(use v1.4)

[![Video](https://img.youtube.com/vi/eX1o_5fpaLc/0.jpg)](https://www.youtube.com/watch?v=eX1o_5fpaLc)

[![Video](https://img.youtube.com/vi/KSrcgBL_NK0/0.jpg)](https://www.youtube.com/watch?v=KSrcgBL_NK0)
(use v1.5)

## 必要要件 (Requirement)

- PV コントローラー (PV controller)
  - Epever 製の Tracer AN/BN/CN シリーズ, LS-B シリーズ  
    (Epever Tracer AN/BN/CN Series, LS-B Series)
- Raspberry PI
- v1.0～v1.5 - Java 8 (v1.0 to v1.5 requires Java8)  
  v1.6 - Java 11 以降 (v1.6 requires Java 11 or later)  

## 使い方 (Usage)

1. Raspberry PI と PV コントローラーを RS485 USB アダプターで接続してください。  
   (Connect Raspberry PI and PV controller with RS485 USB Adapter)

2. application.yml を編集して、PV コントローラーとAmbientの接続情報を記入してください。  
   (Edit application.yml and fills connect information of PV Controller and Ambient)

3. コンパイル&パッケージング (Compile & Packaging)

    ```command
    mvn clean package
    ```

4. jarとapplication.ymlファイルを同一フォルダに置いて、Raspberry PI上で実行  
   (Put jar and application.yml files in same folder, Execute on Raspberry PI)

     ```command
     java -jar mining-powercontroller-1.6.jar
     ```

## 参考情報 (Appendix)

Ambient Channel Setting  
![AmbientChannelSetting](https://user-images.githubusercontent.com/46586035/143958656-64cb38fa-a412-4286-a296-1b8ea527e1d8.png)

Ambient Chart  
![AmbientChart](https://user-images.githubusercontent.com/46586035/143958681-d9aabb26-3a0b-4686-9dcd-91a6cc196cd5.png)
