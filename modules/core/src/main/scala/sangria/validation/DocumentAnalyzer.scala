package sangria.validation

import sangria.ast
import sangria.ast.{FragmentDefinition, FragmentSpread, OperationDefinition}
import sangria.schema.{AstSchemaGenericResolver, ResolverBasedAstSchemaBuilder}
import sangria.util.Cache

import scala.collection.mutable.{ListBuffer, Set => MutableSet}

case class DocumentAnalyzer(document: ast.Document) {
  private val fragmentSpreadsCache = Cache.empty[Int, Vector[ast.FragmentSpread]]
  private val recursivelyReferencedFragmentsCache = Cache.empty[Int, Vector[ast.FragmentDefinition]]

  def resolveDirectives[T](resolvers: AstSchemaGenericResolver[T]*): Vector[T] =
    ResolverBasedAstSchemaBuilder.resolveDirectives[T](document, resolvers: _*)

  def getFragmentSpreads(astNode: ast.SelectionContainer): Vector[FragmentSpread] =
    fragmentSpreadsCache.getOrElseUpdate(astNode.cacheKeyHash, {
      val spreads = ListBuffer[ast.FragmentSpread]()
      val setsToVisit = ValidatorStack.empty[Vector[ast.Selection]]

      setsToVisit.push(astNode.selections)

      while (setsToVisit.nonEmpty) {
        val set = setsToVisit.pop()

        set.foreach {
          case fs: ast.FragmentSpread =>
            spreads += fs
          case cont: ast.SelectionContainer =>
            setsToVisit push cont.selections
        }
      }

      spreads.toVector
    })

  def getRecursivelyReferencedFragments(operation: ast.OperationDefinition): Vector[FragmentDefinition] =
    recursivelyReferencedFragmentsCache.getOrElseUpdate(operation.cacheKeyHash, {
      val frags = ListBuffer[ast.FragmentDefinition]()
      val collectedNames = MutableSet[String]()
      val nodesToVisit = ValidatorStack.empty[ast.SelectionContainer]

      nodesToVisit.push(operation)

      while (nodesToVisit.nonEmpty) {
        val node = nodesToVisit.pop()
        val spreads = getFragmentSpreads(node)

        spreads.foreach { spread =>
          val fragName = spread.name

          if (!collectedNames.contains(fragName)) {
            collectedNames += fragName

            document.fragments.get(fragName) match {
              case Some(frag) =>
                frags += frag
                nodesToVisit.push(frag)
              case None => // do nothing
            }
          }
        }
      }

      frags.toVector
    })

  lazy val separateOperations: Map[Option[String], ast.Document] =
    document.operations.map {
      case (name, definition) => name -> separateOperation(definition)
    }

  def separateOperation(definition: OperationDefinition): ast.Document = {
    val definitions = (definition +: getRecursivelyReferencedFragments(definition)).sortBy(_.location match {
      case Some(pos) => pos.line
      case _ => 0
    })

    document.copy(definitions = definitions)
  }

  def separateOperation(operationName: Option[String]): Option[ast.Document] =
    if (operationName.isEmpty && document.operations.size == 1)
      Some(separateOperation(document.operations.head._2))
    else
      document.operations.get(operationName).map(separateOperation)
}
