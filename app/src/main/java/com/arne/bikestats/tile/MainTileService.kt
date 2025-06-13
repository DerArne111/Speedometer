package com.arne.bikestats.tile

import android.content.ComponentName
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DimensionBuilders
import androidx.wear.protolayout.DimensionBuilders.ContainerDimension
import androidx.wear.protolayout.DimensionBuilders.ExpandedDimensionProp
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.LayoutElementBuilders.VerticalAlignment
import androidx.wear.protolayout.LayoutElementBuilders.VerticalAlignmentProp
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.protolayout.material.Colors
import androidx.wear.protolayout.material.Text
import androidx.wear.protolayout.material.Typography
import androidx.wear.protolayout.material.layouts.PrimaryLayout
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import com.google.android.horologist.annotations.ExperimentalHorologistApi
import com.google.android.horologist.compose.tools.LayoutRootPreview
import com.google.android.horologist.compose.tools.buildDeviceParameters
import com.google.android.horologist.tiles.SuspendingTileService

private const val RESOURCES_VERSION = "0"

/**
 * Skeleton for a tile with no images.
 */
@OptIn(ExperimentalHorologistApi::class)
class MainTileService : SuspendingTileService() {


    override suspend fun resourcesRequest(
        requestParams: RequestBuilders.ResourcesRequest
    ): ResourceBuilders.Resources {
        return ResourceBuilders.Resources.Builder().setVersion(RESOURCES_VERSION).build()
    }

    override suspend fun tileRequest(
        requestParams: RequestBuilders.TileRequest
    ): TileBuilders.Tile {
        val singleTileTimeline = TimelineBuilders.Timeline.Builder().addTimelineEntry(
            TimelineBuilders.TimelineEntry.Builder().setLayout(
                LayoutElementBuilders.Layout.Builder().setRoot(tileLayout(this)).build()
            ).build()
        ).build()


        return TileBuilders.Tile.Builder().setResourcesVersion(RESOURCES_VERSION)
            .setTileTimeline(singleTileTimeline).build()
    }
}

private fun tileLayout(context: Context): LayoutElementBuilders.LayoutElement {
    val layout = PrimaryLayout.Builder(buildDeviceParameters(context.resources))
        .setContent(
            // TODO: Figure out how to get proper formatting
            LayoutElementBuilders.Column.Builder().addContent(
                Text.Builder(context, "")
                    .setColor(argb(Colors.DEFAULT.onSurface))
                    .setTypography(Typography.TYPOGRAPHY_CAPTION1)
                    .build()
            ).addContent(
                Text.Builder(context, "")
                    .setColor(argb(Colors.DEFAULT.onSurface))
                    .setTypography(Typography.TYPOGRAPHY_CAPTION1)
                    .build()
            ).addContent(
                Text.Builder(context, "Speedometer")
                    .setColor(argb(Colors.DEFAULT.onSurface))
                    .setTypography(Typography.TYPOGRAPHY_CAPTION1)
                    .build()
            ).addContent(
                Text.Builder(context, "")
                    .build()
            ).addContent(
                Text.Builder(context, "")
                    .build()
            ).setWidth(ExpandedDimensionProp.Builder().build())
                .setModifiers(
                ModifiersBuilders.Modifiers.Builder()
                    .setClickable(
                        ModifiersBuilders.Clickable.Builder()
                            .setOnClick(
                                ActionBuilders.launchAction(
                                    ComponentName("com.arne.bikestats", "com.arne.bikestats.presentation.MainActivity")
                                )
                            ).build()
                    )
                    .build()
            ).build()
        ).build()
    return layout
}

@Preview(
    device = Devices.WEAR_OS_SMALL_ROUND,
    showSystemUi = true,
    backgroundColor = 0xff000000,
    showBackground = true
)
@Composable
fun TilePreview() {
    LayoutRootPreview(root = tileLayout(LocalContext.current))
}