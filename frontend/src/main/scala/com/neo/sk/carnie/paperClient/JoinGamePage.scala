package com.neo.sk.carnie.paperClient

import com.neo.sk.carnie.paperClient.WebSocketProtocol.WebSocketPara
import com.neo.sk.carnie.util.Component
import org.scalajs.dom
import org.scalajs.dom.html.{Document => _, _}

import scala.xml.Elem

/**
  * Created by dry on 2018/11/28.
  **/
class JoinGamePage(order: String, webSocketPara: WebSocketPara) extends Component {

  def gotoGame() ={
  }
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
              <div class="gameDiv" style="text-align: center;">
                <div style="width: 27%; margin: 20px;">
                  <div style="overflow: hidden;" id="1000000012">
                    <div class="game-not-selected" style="margin-top: 20px;height:100px;width: 100px">
                      <img class="home-img" src="/carnie/static/img/luffy.png"></img>
                        <p style="text-align: center; margin-top: 10px;"> tank</p>
                      </div>
                    </div>
                  </div><div style="width: 27%; margin: 20px;">
                  <div style="overflow: hidden;" id="1000000011">
                    <div class="game-not-selected" style="margin-top: 20px;height:100px;width: 100px">
                      <img class="home-img" src="/carnie/static/img/yang.png"></img>
                        <p style="text-align: center; margin-top: 10px;"> medusa</p>
                      </div>
                    </div>
                  </div><div style="width: 27%; margin: 20px;">
                    <div style="overflow: hidden;" id="1000000013">
                      <div class="game-not-selected" style="margin-top: 20px;height:100px;width: 100px" >
                        <img class="home-img" src="/carnie/static/img/fatTiger.png"></img>
                          <p style="text-align: center; margin-top: 10px;"> carnie</p>
                        </div>
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
                <div>
                  <div class="gameDiv"style="text-align: center;">
                    <div style="width: 27%; margin: 20px;">
                      <div style="overflow: hidden;" id="1000000002">
                        <div class="game-not-selected" style="margin-top: 20px;height:100px;width: 100px">
                          <img class="home-img" src="/carnie/static/img/luffy.png"></img>
                            <p style="text-align: center; margin-top: 10px;"> tank</p>
                          </div>
                        </div>
                      </div><div style="width: 27%; margin: 20px;">
                      <div style="overflow: hidden;" id="1000000001">
                        <div class="game-not-selected" style="margin-top: 20px;height:100px;width: 100px">
                          <img class="home-img" src="/carnie/static/img/yang.png" ></img>
                            <p style="text-align: center; margin-top: 10px;"> medusa</p>
                          </div>
                        </div>
                      </div><div style="width: 27%; margin: 20px;">
                        <div style="overflow: hidden;" id="1000000003">
                          <div class="game-not-selected" style="margin-top: 20px;height:100px;width: 100px">
                            <img class="home-img" src="/carnie/static/img/fatTiger.png" ></img>
                              <p style="text-align: center; margin-top: 10px;"> carnie</p>
                            </div>
                          </div>
                        </div><div style="width: 27%; margin: 20px;">
                          <div style="overflow: hidden;" id="1000000004">
                            <div class="game-not-selected" style="margin-top: 20px;height:100px;width: 100px">
                              <img class="home-img" src="/carnie/static/img/smile.png" ></img>
                                <p style="text-align: center; margin-top: 10px;"> gypsy</p>
                              </div>
                            </div>
                          </div><div style="width: 27%; margin: 20px;">
                            <div style="overflow: hidden;" id="1000000005">
                              <div class="game-not-selected" style="margin-top: 20px;height:100px;width: 100px">
                                <img class="home-img" src="/carnie/static/img/pig.png" ></img>
                                  <p style="text-align: center; margin-top: 10px;"> paradise</p>
                                </div>
                              </div>
                            </div><div style="width: 27%; margin: 20px;">
                              <div style="overflow: hidden;" id="1000000006">
                                <div class="game-not-selected" style="margin-top: 20px;height:100px;width: 100px">
                                  <img class="home-img" src="/carnie/static/img/Bob.png"></img>
                                    <p style="text-align: center; margin-top: 10px;"> thor</p>
                                  </div>
                                </div>
                              </div>
                            </div>
                          </div>
                        </div>
                      <button class="arrow" onclick="">进入游戏</button>
      </body>
    </html>
  }


}
