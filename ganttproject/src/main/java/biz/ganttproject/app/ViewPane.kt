/*
 * Copyright 2024 BarD Software s.r.o., Dmitry Barashev.
 *
 * This file is part of GanttProject, an opensource project management tool.
 *
 * GanttProject is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 * GanttProject is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with GanttProject.  If not, see <http://www.gnu.org/licenses/>.
 */
package biz.ganttproject.app

import biz.ganttproject.FXUtil
import biz.ganttproject.core.option.IntegerOption
import biz.ganttproject.lib.fx.vbox
import javafx.beans.property.SimpleDoubleProperty
import javafx.embed.swing.SwingNode
import javafx.geometry.Orientation
import javafx.geometry.Rectangle2D
import javafx.scene.Node
import javafx.scene.Parent
import javafx.scene.control.SplitPane
import javafx.scene.control.Tab
import javafx.scene.control.TabPane
import javafx.scene.image.Image
import javafx.scene.image.ImageView
import javafx.scene.layout.Pane
import javafx.scene.layout.Priority
import javafx.scene.layout.Region
import net.sourceforge.ganttproject.action.GPAction
import net.sourceforge.ganttproject.chart.Chart
import net.sourceforge.ganttproject.gui.UIFacade
import net.sourceforge.ganttproject.gui.view.ViewProvider
import javax.swing.JComponent
import javax.swing.SwingUtilities

interface View {
  fun refresh() {}

  var isVisible: Boolean
  var isActive: Boolean;
  val chart: Chart
  val id: String

  val createAction: GPAction
  val deleteAction: GPAction
}

class ViewPane {
  private val tabPane = TabPane()
  var onViewCreated: ()->Unit = {}
  fun createComponent(): Parent = tabPane

  fun createView(viewProvider: ViewProvider): View {
    val node = viewProvider.node

    val id = viewProvider.id
    val tab = Tab().also {
      it.content = node
      it.text = viewProvider.getLabel()
      it.id = id
      onViewCreated()
    }
    tabPane.tabs.add(tab)
    tabPane.layout()
    return ViewImpl(tabPane, tab, viewProvider.chart, viewProvider.createAction, viewProvider.deleteAction)
  }
}

fun ViewProvider.getLabel() = localizer.formatText("${this.id}.label")
private val localizer = RootLocalizer.createWithRootKey("view")

private class ViewImpl(
  private val tabPane: TabPane,
  private val tab: Tab,
  override val chart: Chart,
  override val createAction: GPAction,
  override val deleteAction: GPAction): View {

  override var isVisible: Boolean = true
    set(value) {
      FXUtil.runLater {
        if (value.not() && field) { tabPane.tabs.remove(tab) }
        if (value && field.not()) { tabPane.tabs.add(tab) }
        field = value
      }
    }

  override var isActive: Boolean
    get() = tab.isSelected
    set(value) {
      if (value) {
        FXUtil.runLater {
          tabPane.selectionModel.select(tab)
        }
      }
    }
  override val id: String
    get() = tab.id
}

class UninitializedView(private val viewPane: ViewPane, private val viewProvider: ViewProvider): View {
  override var isVisible: Boolean = false
    set(value) {
      FXUtil.runLater {
        viewPane.createView(viewProvider)
      }
    }
  override var isActive: Boolean = false
  override val chart: Chart
    get() = error("Not supposed to be called")
  override val id = viewProvider.id
  override val createAction: GPAction
    get() = TODO("Not yet implemented")
  override val deleteAction: GPAction
    get() = TODO("Not yet implemented")

}

/**
 * Components included into the view: image, split pane, a table node.
 */
data class ViewComponents(val image: Pane, val splitPane: SplitPane, val table: Node) {
  // Indicates if the split divider was initialized successfully.
  private var isDividerInitialized = false

  fun initializeDivider(columnsWidth: Double) {
    // We need to initialize the split pane divider to fit all table columns.
    // For that, we need to know the split pane width. Unfortunately, when the split pane is being inserted into the
    // node tree, it is zero until something happens. This hack adds a one-off listener to the split pane width to
    // initialize it when it changes from zero to something meaningful.
    if (!isDividerInitialized) {
      if (splitPane.width != 0.0) {
        splitPane.setDividerPosition(0, columnsWidth / splitPane.width)
        isDividerInitialized = true
      } else {
        splitPane.widthProperty().addListener { _, oldValue, newValue ->
          if (oldValue == 0.0 && newValue != 0.0) {
            splitPane.setDividerPosition(0, columnsWidth / splitPane.width)
            isDividerInitialized = true
          }
        }
      }
    }
  }
}

fun createViewComponents(
  tableToolbarBuilder: ()-> Region,
  tableBuilder: ()->Node,
  chartToolbarBuilder: ()-> Region,
  chartBuilder: ()-> JComponent,
  dpiOption: IntegerOption): ViewComponents {

  val defaultScaledHeight =
    (UIFacade.DEFAULT_LOGO.iconHeight * dpiOption.value / (1f * UIFacade.DEFAULT_DPI)).toInt()
  val image = Image(ViewPane::class.java.getResourceAsStream("/icons/big.png"))
  val imageView = ImageView().apply {
    this.image = image
    fitHeight = defaultScaledHeight.toDouble()
    //isPreserveRatio = true
    viewport = Rectangle2D(0.0, 0.0, image.width, defaultScaledHeight.toDouble())

  }
  val imagePane = Pane(imageView).also { it.minHeight = defaultScaledHeight.toDouble() }

  val table = tableBuilder()
  val splitPane = SplitPane().also {split ->
    var maxToolbarHeight = SimpleDoubleProperty(0.0)
    split.orientation = Orientation.HORIZONTAL
    split.items.add(vbox {
      add(tableToolbarBuilder().also {
        it.heightProperty().subscribe { v ->
          if (v.toDouble() > maxToolbarHeight.value) {
            maxToolbarHeight.value = v.toDouble()
          }
        }
        it.prefHeightProperty().bind(maxToolbarHeight)
      })
      add(imagePane)
      add(table, null, growth = Priority.ALWAYS)
    })

    val swingNode = SwingNode()
    val right = vbox {
      add(chartToolbarBuilder().also {
        it.heightProperty().subscribe { v ->
          if (v.toDouble() > maxToolbarHeight.value) {
            maxToolbarHeight.value = v.toDouble()
          }
        }
        it.prefHeightProperty().bind(maxToolbarHeight)
      })
      add(swingNode, null, Priority.ALWAYS)
    }
    SwingUtilities.invokeLater { swingNode.content = chartBuilder() }
    split.items.add(right)
    split.setDividerPosition(0, 0.5)
//    split.addEventHandler(KeyEvent.KEY_PRESSED) { evt ->
//      println("split: evt=$evt")
//      println("accelerators=${DialogPlacement.applicationWindow?.scene?.accelerators}")
//      DialogPlacement.applicationWindow?.scene?.accelerators?.entries?.forEach {
//        if (it.key.match(evt)) {
//          it.value.run()
//        }
//      }
//    }
  }
  return ViewComponents(image = imagePane, splitPane = splitPane, table = table)
}