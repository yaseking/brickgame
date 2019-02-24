package brickgame.frontendAdmin.components

/**
  * User: Jason
  * Date: 2018/12/19
  * Time: 9:51
  */
import brickgame.frontendAdmin.Routes.Admin
import brickgame.frontendAdmin.pages._
import brickgame.frontendAdmin.util.{Component, Http}
import brickgame.frontendAdmin.styles.Demo2Styles.menuHorizontalLink

import scala.xml.Elem

object Header extends Component{

  val ls = List(
    ("用户信息", CurrentDataPage.locationHashString),
    ("数据统计", ViewPage.locationHashString),
//    ("GPU预约情况", GPUOrderPage.locationHashString),
//    ("GPU使用异常", GPUOnWorkPage.locationHashString),
//    ("金币记录",CoinRecordPage.locationHashString)
  )

  def logout(): Unit = {
    val url = Admin.logout
    Http.get(url)
  }

  override def render: Elem = {
    <div>
      <div style="text-align:center;">
        <nav class="nav nav-tabs" style="text-align:center;">
          {ls.map { case (name, hash) =>
          <li>
            <a style="font-weight:bold;font-size:150%;margin:5px 15px;" class={menuHorizontalLink.htmlClass} href={hash}>
              {name}
            </a>
          </li>
        }}
          <li style="float:Right;">
            <a style="font-weight:bold;font-size:150%;margin:5px 15px;" onclick={() => logout()} class={menuHorizontalLink.htmlClass} href={"#/LoginPage"}>
              退出
            </a>
          </li>
        </nav>
      </div>
    </div>
  }


}