import com.neo.sk.carnie.Point

val baseDirection = List(Point(-1, 0), Point(1, 0), Point(0, -1),Point(0, 1))

def findShortestPath(start:Point, end: Point, fieldBoundary: List[Point]): Unit = {
  var initDirection = List.empty[Point]
  baseDirection.foreach{p =>
    if(fieldBoundary.contains(start + p)) initDirection = p :: initDirection}
  if(initDirection.lengthCompare(2) == 0){
    val route1 = getShortest(start + initDirection.head, end, fieldBoundary, Nil, initDirection.head)
//    val route2 = getShortest(start + initDirection.last, end, fieldBoundary, Nil)
    println(route1)
//    println(route2)
  } else {
    List.empty[Point]
  }
}

def getShortest(start: Point, end: Point, fieldBoundary: List[Point], targetPath: List[Point], lastDirection: Point): List[Point] ={
  var res = targetPath
  val resetDirection = if(lastDirection.x != 0) Point(-lastDirection.x, lastDirection.y) else Point(lastDirection.x, -lastDirection.y)
  println(start)
  while(start != end){
    var direction = Point(-1, -1)
    baseDirection.filterNot(_ == resetDirection).foreach{d => if(fieldBoundary.contains(start + d)) direction = d}
    if(direction != Point(-1,-1)){
      println(direction)
//      res = getShortest(start + direction, end, fieldBoundary, start + direction :: targetPath, direction)
    } else {
      return List.empty[Point]
    }
  }
  res
}

findShortestPath(Point(0,0), Point(2,0),
  List(Point(0,0),Point(1,0),Point(2,0),Point(0,1),Point(0,2),Point(2,1),Point(2,2),Point(1,2)))