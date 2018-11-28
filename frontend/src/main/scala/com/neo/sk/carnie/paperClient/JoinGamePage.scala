package com.neo.sk.carnie.paperClient

import com.neo.sk.carnie.paperClient.WebSocketProtocol.WebSocketPara
import com.neo.sk.carnie.util.Component

import scala.xml.Elem

/**
  * Created by dry on 2018/11/28.
  **/
class JoinGamePage(order: String, webSocketPara: WebSocketPara) extends Component {

  override def render: Elem = {
    <html>
      <body>
        <div style="text-align: center;">
          <div  id="form">
            <h1 style="font-family: Verdana;font-size: 30px;">欢迎来到carnie</h1>
          </div>
          <div style="overflow: hidden;" >
            <div  style="margin-top: 20px;">
              <p style="text-align: center; margin-top: 10px;"> 选择模式</p>
            </div>
            <div>
              <div class="gameDiv">
                <div style="width: 27%; margin: 20px;">
                  <div style="overflow: hidden;" id="1000000002">
                    <div class="game-not-selected" style="margin-top: 20px;">
                      <!--<img class="home-img" src="http://flowdev.neoap.com/esheep/static/img/game.png">-->
                      <p style="text-align: center; margin-top: 10px;"> tank</p>
                    </div>
                  </div>
                </div><div style="width: 27%; margin: 20px;">
                <div style="overflow: hidden;" id="1000000001">
                  <div class="game-not-selected" style="margin-top: 20px;">
                    <!--<img class="home-img" lsrc="http://flowdev.neoap.com/esheep/static/img/game.png">-->
                    <p style="text-align: center; margin-top: 10px;"> medusa</p>
                  </div>
                </div>
              </div><div style="width: 27%; margin: 20px;">
                <div style="overflow: hidden;" id="1000000003">
                  <div class="game-not-selected" style="margin-top: 20px;">
                    <!--<img class="home-img" src="http://flowdev.neoap.com/esheep/static/img/game.png">-->
                    <p style="text-align: center; margin-top: 10px;"> carnie</p>
                  </div>
                </div>
              </div><div style="width: 27%; margin: 20px;">
                <div style="overflow: hidden;" id="1000000004">
                  <div class="game-not-selected" style="margin-top: 20px;">
                    <!--<img class="home-img" src="http://flowdev.neoap.com/esheep/static/img/game.png">-->
                    <p style="text-align: center; margin-top: 10px;"> gypsy</p>
                  </div>
                </div>
              </div><div style="width: 27%; margin: 20px;">
                <div style="overflow: hidden;" id="1000000005">
                  <div class="game-not-selected" style="margin-top: 20px;">
                    <!--<img class="home-img" src="http://flowdev.neoap.com/esheep/static/img/game.png">-->
                    <p style="text-align: center; margin-top: 10px;"> paradise</p>
                  </div>
                </div>
              </div><div style="width: 27%; margin: 20px;">
                <div style="overflow: hidden;" id="1000000006">
                  <div class="game-not-selected" style="margin-top: 20px;">
                    <!--<img class="home-img" src="http://flowdev.neoap.com/esheep/static/img/game.png">-->
                    <p style="text-align: center; margin-top: 10px;"> thor</p>
                  </div>
                </div>
              </div>
              </div>
            </div>
          </div>
          <div style="overflow: hidden;" >
            <div style="margin-top: 20px;">
              <p style="text-align: center; margin-top: 10px;"> 选择头像</p>
            </div>
          </div>
          <button class="arrow" onclick="f()">进入游戏</button>
          </div>
      </body>
    </html>
  }


}
