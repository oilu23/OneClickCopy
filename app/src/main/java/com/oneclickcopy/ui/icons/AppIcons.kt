package com.oneclickcopy.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType.Companion.EvenOdd
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * The Material icons the app uses, copied from the Material Icons sources
 * (Apache License 2.0, The Android Open Source Project).
 *
 * Shared by the Android and Windows apps. Bundling just these 25 replaces the
 * material-icons-extended library, which ships over 10,000 icons (79 MB on
 * the desktop, where nothing shrinks the jar) and is no longer updated.
 * Built with plain ImageVector.Builder so no icons library is needed at all.
 */
object AppIcons {

    val ArrowBack: ImageVector by lazy {
        icon("ArrowBack", autoMirror = true) {
    path(fill = Black) {
        moveTo(20.0f, 11.0f)
        horizontalLineTo(7.83f)
        lineToRelative(5.59f, -5.59f)
        lineTo(12.0f, 4.0f)
        lineToRelative(-8.0f, 8.0f)
        lineToRelative(8.0f, 8.0f)
        lineToRelative(1.41f, -1.41f)
        lineTo(7.83f, 13.0f)
        horizontalLineTo(20.0f)
        verticalLineToRelative(-2.0f)
        close()
    }
        }
    }

    val Logout: ImageVector by lazy {
        icon("Logout", autoMirror = true) {
    path(fill = Black) {
        moveTo(17.0f, 7.0f)
        lineToRelative(-1.41f, 1.41f)
        lineTo(18.17f, 11.0f)
        horizontalLineTo(8.0f)
        verticalLineToRelative(2.0f)
        horizontalLineToRelative(10.17f)
        lineToRelative(-2.58f, 2.58f)
        lineTo(17.0f, 17.0f)
        lineToRelative(5.0f, -5.0f)
        close()
        moveTo(4.0f, 5.0f)
        horizontalLineToRelative(8.0f)
        verticalLineTo(3.0f)
        horizontalLineTo(4.0f)
        curveToRelative(-1.1f, 0.0f, -2.0f, 0.9f, -2.0f, 2.0f)
        verticalLineToRelative(14.0f)
        curveToRelative(0.0f, 1.1f, 0.9f, 2.0f, 2.0f, 2.0f)
        horizontalLineToRelative(8.0f)
        verticalLineToRelative(-2.0f)
        horizontalLineTo(4.0f)
        verticalLineTo(5.0f)
        close()
    }
        }
    }

    val Undo: ImageVector by lazy {
        icon("Undo", autoMirror = true) {
    path(fill = Black) {
        moveTo(12.5f, 8.0f)
        curveToRelative(-2.65f, 0.0f, -5.05f, 0.99f, -6.9f, 2.6f)
        lineTo(2.0f, 7.0f)
        verticalLineToRelative(9.0f)
        horizontalLineToRelative(9.0f)
        lineToRelative(-3.62f, -3.62f)
        curveToRelative(1.39f, -1.16f, 3.16f, -1.88f, 5.12f, -1.88f)
        curveToRelative(3.54f, 0.0f, 6.55f, 2.31f, 7.6f, 5.5f)
        lineToRelative(2.37f, -0.78f)
        curveTo(21.08f, 11.03f, 17.15f, 8.0f, 12.5f, 8.0f)
        close()
    }
        }
    }

    val AccountCircle: ImageVector by lazy {
        icon("AccountCircle") {
    path(fill = Black) {
        moveTo(12.0f, 2.0f)
        curveTo(6.48f, 2.0f, 2.0f, 6.48f, 2.0f, 12.0f)
        reflectiveCurveToRelative(4.48f, 10.0f, 10.0f, 10.0f)
        reflectiveCurveToRelative(10.0f, -4.48f, 10.0f, -10.0f)
        reflectiveCurveTo(17.52f, 2.0f, 12.0f, 2.0f)
        close()
        moveTo(12.0f, 6.0f)
        curveToRelative(1.93f, 0.0f, 3.5f, 1.57f, 3.5f, 3.5f)
        reflectiveCurveTo(13.93f, 13.0f, 12.0f, 13.0f)
        reflectiveCurveToRelative(-3.5f, -1.57f, -3.5f, -3.5f)
        reflectiveCurveTo(10.07f, 6.0f, 12.0f, 6.0f)
        close()
        moveTo(12.0f, 20.0f)
        curveToRelative(-2.03f, 0.0f, -4.43f, -0.82f, -6.14f, -2.88f)
        curveTo(7.55f, 15.8f, 9.68f, 15.0f, 12.0f, 15.0f)
        reflectiveCurveToRelative(4.45f, 0.8f, 6.14f, 2.12f)
        curveTo(16.43f, 19.18f, 14.03f, 20.0f, 12.0f, 20.0f)
        close()
    }
        }
    }

    val Add: ImageVector by lazy {
        icon("Add") {
    path(fill = Black) {
        moveTo(19.0f, 13.0f)
        horizontalLineToRelative(-6.0f)
        verticalLineToRelative(6.0f)
        horizontalLineToRelative(-2.0f)
        verticalLineToRelative(-6.0f)
        horizontalLineTo(5.0f)
        verticalLineToRelative(-2.0f)
        horizontalLineToRelative(6.0f)
        verticalLineTo(5.0f)
        horizontalLineToRelative(2.0f)
        verticalLineToRelative(6.0f)
        horizontalLineToRelative(6.0f)
        verticalLineToRelative(2.0f)
        close()
    }
        }
    }

    val Close: ImageVector by lazy {
        icon("Close") {
    path(fill = Black) {
        moveTo(19.0f, 6.41f)
        lineTo(17.59f, 5.0f)
        lineTo(12.0f, 10.59f)
        lineTo(6.41f, 5.0f)
        lineTo(5.0f, 6.41f)
        lineTo(10.59f, 12.0f)
        lineTo(5.0f, 17.59f)
        lineTo(6.41f, 19.0f)
        lineTo(12.0f, 13.41f)
        lineTo(17.59f, 19.0f)
        lineTo(19.0f, 17.59f)
        lineTo(13.41f, 12.0f)
        close()
    }
        }
    }

    val CloudDone: ImageVector by lazy {
        icon("CloudDone") {
    path(fill = Black) {
        moveTo(19.35f, 10.04f)
        curveTo(18.67f, 6.59f, 15.64f, 4.0f, 12.0f, 4.0f)
        curveTo(9.11f, 4.0f, 6.6f, 5.64f, 5.35f, 8.04f)
        curveTo(2.34f, 8.36f, 0.0f, 10.91f, 0.0f, 14.0f)
        curveToRelative(0.0f, 3.31f, 2.69f, 6.0f, 6.0f, 6.0f)
        horizontalLineToRelative(13.0f)
        curveToRelative(2.76f, 0.0f, 5.0f, -2.24f, 5.0f, -5.0f)
        curveToRelative(0.0f, -2.64f, -2.05f, -4.78f, -4.65f, -4.96f)
        close()
        moveTo(10.0f, 17.0f)
        lineToRelative(-3.5f, -3.5f)
        lineToRelative(1.41f, -1.41f)
        lineTo(10.0f, 14.17f)
        lineTo(15.18f, 9.0f)
        lineToRelative(1.41f, 1.41f)
        lineTo(10.0f, 17.0f)
        close()
    }
        }
    }

    val CloudOff: ImageVector by lazy {
        icon("CloudOff") {
    path(fill = Black) {
        moveTo(19.35f, 10.04f)
        curveTo(18.67f, 6.59f, 15.64f, 4.0f, 12.0f, 4.0f)
        curveToRelative(-1.48f, 0.0f, -2.85f, 0.43f, -4.01f, 1.17f)
        lineToRelative(1.46f, 1.46f)
        curveTo(10.21f, 6.23f, 11.08f, 6.0f, 12.0f, 6.0f)
        curveToRelative(3.04f, 0.0f, 5.5f, 2.46f, 5.5f, 5.5f)
        verticalLineToRelative(0.5f)
        horizontalLineTo(19.0f)
        curveToRelative(1.66f, 0.0f, 3.0f, 1.34f, 3.0f, 3.0f)
        curveToRelative(0.0f, 1.13f, -0.64f, 2.11f, -1.56f, 2.62f)
        lineToRelative(1.45f, 1.45f)
        curveTo(23.16f, 18.16f, 24.0f, 16.68f, 24.0f, 15.0f)
        curveToRelative(0.0f, -2.64f, -2.05f, -4.78f, -4.65f, -4.96f)
        close()
        moveTo(3.0f, 5.27f)
        lineToRelative(2.75f, 2.74f)
        curveTo(2.56f, 8.15f, 0.0f, 10.77f, 0.0f, 14.0f)
        curveToRelative(0.0f, 3.31f, 2.69f, 6.0f, 6.0f, 6.0f)
        horizontalLineToRelative(11.73f)
        lineToRelative(2.0f, 2.0f)
        lineTo(21.0f, 20.73f)
        lineTo(4.27f, 4.0f)
        lineTo(3.0f, 5.27f)
        close()
        moveTo(7.73f, 10.0f)
        lineToRelative(8.0f, 8.0f)
        horizontalLineTo(6.0f)
        curveToRelative(-2.21f, 0.0f, -4.0f, -1.79f, -4.0f, -4.0f)
        reflectiveCurveToRelative(1.79f, -4.0f, 4.0f, -4.0f)
        horizontalLineToRelative(1.73f)
        close()
    }
        }
    }

    val CloudQueue: ImageVector by lazy {
        icon("CloudQueue") {
    path(fill = Black) {
        moveTo(19.35f, 10.04f)
        curveTo(18.67f, 6.59f, 15.64f, 4.0f, 12.0f, 4.0f)
        curveTo(9.11f, 4.0f, 6.6f, 5.64f, 5.35f, 8.04f)
        curveTo(2.34f, 8.36f, 0.0f, 10.91f, 0.0f, 14.0f)
        curveToRelative(0.0f, 3.31f, 2.69f, 6.0f, 6.0f, 6.0f)
        horizontalLineToRelative(13.0f)
        curveToRelative(2.76f, 0.0f, 5.0f, -2.24f, 5.0f, -5.0f)
        curveToRelative(0.0f, -2.64f, -2.05f, -4.78f, -4.65f, -4.96f)
        close()
        moveTo(19.0f, 18.0f)
        horizontalLineTo(6.0f)
        curveToRelative(-2.21f, 0.0f, -4.0f, -1.79f, -4.0f, -4.0f)
        reflectiveCurveToRelative(1.79f, -4.0f, 4.0f, -4.0f)
        horizontalLineToRelative(0.71f)
        curveTo(7.37f, 7.69f, 9.48f, 6.0f, 12.0f, 6.0f)
        curveToRelative(3.04f, 0.0f, 5.5f, 2.46f, 5.5f, 5.5f)
        verticalLineToRelative(0.5f)
        horizontalLineTo(19.0f)
        curveToRelative(1.66f, 0.0f, 3.0f, 1.34f, 3.0f, 3.0f)
        reflectiveCurveToRelative(-1.34f, 3.0f, -3.0f, 3.0f)
        close()
    }
        }
    }

    val CloudSync: ImageVector by lazy {
        icon("CloudSync") {
    path(fill = Black) {
        moveTo(21.5f, 14.98f)
        curveToRelative(-0.02f, 0.0f, -0.03f, 0.0f, -0.05f, 0.01f)
        curveTo(21.2f, 13.3f, 19.76f, 12.0f, 18.0f, 12.0f)
        curveToRelative(-1.4f, 0.0f, -2.6f, 0.83f, -3.16f, 2.02f)
        curveTo(13.26f, 14.1f, 12.0f, 15.4f, 12.0f, 17.0f)
        curveToRelative(0.0f, 1.66f, 1.34f, 3.0f, 3.0f, 3.0f)
        lineToRelative(6.5f, -0.02f)
        curveToRelative(1.38f, 0.0f, 2.5f, -1.12f, 2.5f, -2.5f)
        reflectiveCurveTo(22.88f, 14.98f, 21.5f, 14.98f)
        close()
        moveTo(10.0f, 4.26f)
        verticalLineToRelative(2.09f)
        curveTo(7.67f, 7.18f, 6.0f, 9.39f, 6.0f, 12.0f)
        curveToRelative(0.0f, 1.77f, 0.78f, 3.34f, 2.0f, 4.44f)
        verticalLineTo(14.0f)
        horizontalLineToRelative(2.0f)
        verticalLineToRelative(6.0f)
        horizontalLineTo(4.0f)
        verticalLineToRelative(-2.0f)
        horizontalLineToRelative(2.73f)
        curveTo(5.06f, 16.54f, 4.0f, 14.4f, 4.0f, 12.0f)
        curveTo(4.0f, 8.27f, 6.55f, 5.15f, 10.0f, 4.26f)
        close()
        moveTo(20.0f, 6.0f)
        horizontalLineToRelative(-2.73f)
        curveToRelative(1.43f, 1.26f, 2.41f, 3.01f, 2.66f, 5.0f)
        lineToRelative(-2.02f, 0.0f)
        curveTo(17.68f, 9.64f, 16.98f, 8.45f, 16.0f, 7.56f)
        verticalLineTo(10.0f)
        horizontalLineToRelative(-2.0f)
        verticalLineTo(4.0f)
        horizontalLineToRelative(6.0f)
        verticalLineTo(6.0f)
        close()
    }
        }
    }

    val CloudUpload: ImageVector by lazy {
        icon("CloudUpload") {
    path(fill = Black) {
        moveTo(19.35f, 10.04f)
        curveTo(18.67f, 6.59f, 15.64f, 4.0f, 12.0f, 4.0f)
        curveTo(9.11f, 4.0f, 6.6f, 5.64f, 5.35f, 8.04f)
        curveTo(2.34f, 8.36f, 0.0f, 10.91f, 0.0f, 14.0f)
        curveToRelative(0.0f, 3.31f, 2.69f, 6.0f, 6.0f, 6.0f)
        horizontalLineToRelative(13.0f)
        curveToRelative(2.76f, 0.0f, 5.0f, -2.24f, 5.0f, -5.0f)
        curveToRelative(0.0f, -2.64f, -2.05f, -4.78f, -4.65f, -4.96f)
        close()
        moveTo(14.0f, 13.0f)
        verticalLineToRelative(4.0f)
        horizontalLineToRelative(-4.0f)
        verticalLineToRelative(-4.0f)
        horizontalLineTo(7.0f)
        lineToRelative(5.0f, -5.0f)
        lineToRelative(5.0f, 5.0f)
        horizontalLineToRelative(-3.0f)
        close()
    }
        }
    }

    val DragIndicator: ImageVector by lazy {
        icon("DragIndicator") {
    path(fill = Black) {
        moveTo(11.0f, 18.0f)
        curveToRelative(0.0f, 1.1f, -0.9f, 2.0f, -2.0f, 2.0f)
        reflectiveCurveToRelative(-2.0f, -0.9f, -2.0f, -2.0f)
        reflectiveCurveToRelative(0.9f, -2.0f, 2.0f, -2.0f)
        reflectiveCurveToRelative(2.0f, 0.9f, 2.0f, 2.0f)
        close()
        moveTo(9.0f, 10.0f)
        curveToRelative(-1.1f, 0.0f, -2.0f, 0.9f, -2.0f, 2.0f)
        reflectiveCurveToRelative(0.9f, 2.0f, 2.0f, 2.0f)
        reflectiveCurveToRelative(2.0f, -0.9f, 2.0f, -2.0f)
        reflectiveCurveToRelative(-0.9f, -2.0f, -2.0f, -2.0f)
        close()
        moveTo(9.0f, 4.0f)
        curveToRelative(-1.1f, 0.0f, -2.0f, 0.9f, -2.0f, 2.0f)
        reflectiveCurveToRelative(0.9f, 2.0f, 2.0f, 2.0f)
        reflectiveCurveToRelative(2.0f, -0.9f, 2.0f, -2.0f)
        reflectiveCurveToRelative(-0.9f, -2.0f, -2.0f, -2.0f)
        close()
        moveTo(15.0f, 8.0f)
        curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
        reflectiveCurveToRelative(-0.9f, -2.0f, -2.0f, -2.0f)
        reflectiveCurveToRelative(-2.0f, 0.9f, -2.0f, 2.0f)
        reflectiveCurveToRelative(0.9f, 2.0f, 2.0f, 2.0f)
        close()
        moveTo(15.0f, 10.0f)
        curveToRelative(-1.1f, 0.0f, -2.0f, 0.9f, -2.0f, 2.0f)
        reflectiveCurveToRelative(0.9f, 2.0f, 2.0f, 2.0f)
        reflectiveCurveToRelative(2.0f, -0.9f, 2.0f, -2.0f)
        reflectiveCurveToRelative(-0.9f, -2.0f, -2.0f, -2.0f)
        close()
        moveTo(15.0f, 16.0f)
        curveToRelative(-1.1f, 0.0f, -2.0f, 0.9f, -2.0f, 2.0f)
        reflectiveCurveToRelative(0.9f, 2.0f, 2.0f, 2.0f)
        reflectiveCurveToRelative(2.0f, -0.9f, 2.0f, -2.0f)
        reflectiveCurveToRelative(-0.9f, -2.0f, -2.0f, -2.0f)
        close()
    }
        }
    }

    val EditNote: ImageVector by lazy {
        icon("EditNote") {
    path(fill = Black) {
        moveTo(3.0f, 10.0f)
        horizontalLineToRelative(11.0f)
        verticalLineToRelative(2.0f)
        horizontalLineTo(3.0f)
        verticalLineTo(10.0f)
        close()
        moveTo(3.0f, 8.0f)
        horizontalLineToRelative(11.0f)
        verticalLineTo(6.0f)
        horizontalLineTo(3.0f)
        verticalLineTo(8.0f)
        close()
        moveTo(3.0f, 16.0f)
        horizontalLineToRelative(7.0f)
        verticalLineToRelative(-2.0f)
        horizontalLineTo(3.0f)
        verticalLineTo(16.0f)
        close()
        moveTo(18.01f, 12.87f)
        lineToRelative(0.71f, -0.71f)
        curveToRelative(0.39f, -0.39f, 1.02f, -0.39f, 1.41f, 0.0f)
        lineToRelative(0.71f, 0.71f)
        curveToRelative(0.39f, 0.39f, 0.39f, 1.02f, 0.0f, 1.41f)
        lineToRelative(-0.71f, 0.71f)
        lineTo(18.01f, 12.87f)
        close()
        moveTo(17.3f, 13.58f)
        lineToRelative(-5.3f, 5.3f)
        verticalLineTo(21.0f)
        horizontalLineToRelative(2.12f)
        lineToRelative(5.3f, -5.3f)
        lineTo(17.3f, 13.58f)
        close()
    }
        }
    }

    val ErrorOutline: ImageVector by lazy {
        icon("ErrorOutline") {
    path(fill = Black) {
        moveTo(11.0f, 15.0f)
        horizontalLineToRelative(2.0f)
        verticalLineToRelative(2.0f)
        horizontalLineToRelative(-2.0f)
        close()
        moveTo(11.0f, 7.0f)
        horizontalLineToRelative(2.0f)
        verticalLineToRelative(6.0f)
        horizontalLineToRelative(-2.0f)
        close()
        moveTo(11.99f, 2.0f)
        curveTo(6.47f, 2.0f, 2.0f, 6.48f, 2.0f, 12.0f)
        reflectiveCurveToRelative(4.47f, 10.0f, 9.99f, 10.0f)
        curveTo(17.52f, 22.0f, 22.0f, 17.52f, 22.0f, 12.0f)
        reflectiveCurveTo(17.52f, 2.0f, 11.99f, 2.0f)
        close()
        moveTo(12.0f, 20.0f)
        curveToRelative(-4.42f, 0.0f, -8.0f, -3.58f, -8.0f, -8.0f)
        reflectiveCurveToRelative(3.58f, -8.0f, 8.0f, -8.0f)
        reflectiveCurveToRelative(8.0f, 3.58f, 8.0f, 8.0f)
        reflectiveCurveToRelative(-3.58f, 8.0f, -8.0f, 8.0f)
        close()
    }
        }
    }

    val FileDownload: ImageVector by lazy {
        icon("FileDownload") {
    path(fill = Black) {
        moveTo(19.0f, 9.0f)
        horizontalLineToRelative(-4.0f)
        verticalLineTo(3.0f)
        horizontalLineTo(9.0f)
        verticalLineToRelative(6.0f)
        horizontalLineTo(5.0f)
        lineToRelative(7.0f, 7.0f)
        lineToRelative(7.0f, -7.0f)
        close()
        moveTo(5.0f, 18.0f)
        verticalLineToRelative(2.0f)
        horizontalLineToRelative(14.0f)
        verticalLineToRelative(-2.0f)
        horizontalLineTo(5.0f)
        close()
    }
        }
    }

    val FileUpload: ImageVector by lazy {
        icon("FileUpload") {
    path(fill = Black) {
        moveTo(9.0f, 16.0f)
        horizontalLineToRelative(6.0f)
        verticalLineToRelative(-6.0f)
        horizontalLineToRelative(4.0f)
        lineToRelative(-7.0f, -7.0f)
        lineToRelative(-7.0f, 7.0f)
        horizontalLineToRelative(4.0f)
        close()
        moveTo(5.0f, 18.0f)
        horizontalLineToRelative(14.0f)
        verticalLineToRelative(2.0f)
        lineTo(5.0f, 20.0f)
        close()
    }
        }
    }

    val MoreVert: ImageVector by lazy {
        icon("MoreVert") {
    path(fill = Black) {
        moveTo(12.0f, 8.0f)
        curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
        reflectiveCurveToRelative(-0.9f, -2.0f, -2.0f, -2.0f)
        reflectiveCurveToRelative(-2.0f, 0.9f, -2.0f, 2.0f)
        reflectiveCurveToRelative(0.9f, 2.0f, 2.0f, 2.0f)
        close()
        moveTo(12.0f, 10.0f)
        curveToRelative(-1.1f, 0.0f, -2.0f, 0.9f, -2.0f, 2.0f)
        reflectiveCurveToRelative(0.9f, 2.0f, 2.0f, 2.0f)
        reflectiveCurveToRelative(2.0f, -0.9f, 2.0f, -2.0f)
        reflectiveCurveToRelative(-0.9f, -2.0f, -2.0f, -2.0f)
        close()
        moveTo(12.0f, 16.0f)
        curveToRelative(-1.1f, 0.0f, -2.0f, 0.9f, -2.0f, 2.0f)
        reflectiveCurveToRelative(0.9f, 2.0f, 2.0f, 2.0f)
        reflectiveCurveToRelative(2.0f, -0.9f, 2.0f, -2.0f)
        reflectiveCurveToRelative(-0.9f, -2.0f, -2.0f, -2.0f)
        close()
    }
        }
    }

    val PushPin: ImageVector by lazy {
        icon("PushPin") {
    path(fill = Black, pathFillType = EvenOdd) {
        moveTo(16.0f, 9.0f)
        verticalLineTo(4.0f)
        lineToRelative(1.0f, 0.0f)
        curveToRelative(0.55f, 0.0f, 1.0f, -0.45f, 1.0f, -1.0f)
        verticalLineToRelative(0.0f)
        curveToRelative(0.0f, -0.55f, -0.45f, -1.0f, -1.0f, -1.0f)
        horizontalLineTo(7.0f)
        curveTo(6.45f, 2.0f, 6.0f, 2.45f, 6.0f, 3.0f)
        verticalLineToRelative(0.0f)
        curveToRelative(0.0f, 0.55f, 0.45f, 1.0f, 1.0f, 1.0f)
        lineToRelative(1.0f, 0.0f)
        verticalLineToRelative(5.0f)
        curveToRelative(0.0f, 1.66f, -1.34f, 3.0f, -3.0f, 3.0f)
        horizontalLineToRelative(0.0f)
        verticalLineToRelative(2.0f)
        horizontalLineToRelative(5.97f)
        verticalLineToRelative(7.0f)
        lineToRelative(1.0f, 1.0f)
        lineToRelative(1.0f, -1.0f)
        verticalLineToRelative(-7.0f)
        horizontalLineTo(19.0f)
        verticalLineToRelative(-2.0f)
        horizontalLineToRelative(0.0f)
        curveTo(17.34f, 12.0f, 16.0f, 10.66f, 16.0f, 9.0f)
        close()
    }
        }
    }

    val Refresh: ImageVector by lazy {
        icon("Refresh") {
    path(fill = Black) {
        moveTo(17.65f, 6.35f)
        curveTo(16.2f, 4.9f, 14.21f, 4.0f, 12.0f, 4.0f)
        curveToRelative(-4.42f, 0.0f, -7.99f, 3.58f, -7.99f, 8.0f)
        reflectiveCurveToRelative(3.57f, 8.0f, 7.99f, 8.0f)
        curveToRelative(3.73f, 0.0f, 6.84f, -2.55f, 7.73f, -6.0f)
        horizontalLineToRelative(-2.08f)
        curveToRelative(-0.82f, 2.33f, -3.04f, 4.0f, -5.65f, 4.0f)
        curveToRelative(-3.31f, 0.0f, -6.0f, -2.69f, -6.0f, -6.0f)
        reflectiveCurveToRelative(2.69f, -6.0f, 6.0f, -6.0f)
        curveToRelative(1.66f, 0.0f, 3.14f, 0.69f, 4.22f, 1.78f)
        lineTo(13.0f, 11.0f)
        horizontalLineToRelative(7.0f)
        verticalLineTo(4.0f)
        lineToRelative(-2.35f, 2.35f)
        close()
    }
        }
    }

    val Search: ImageVector by lazy {
        icon("Search") {
    path(fill = Black) {
        moveTo(15.5f, 14.0f)
        horizontalLineToRelative(-0.79f)
        lineToRelative(-0.28f, -0.27f)
        curveTo(15.41f, 12.59f, 16.0f, 11.11f, 16.0f, 9.5f)
        curveTo(16.0f, 5.91f, 13.09f, 3.0f, 9.5f, 3.0f)
        reflectiveCurveTo(3.0f, 5.91f, 3.0f, 9.5f)
        reflectiveCurveTo(5.91f, 16.0f, 9.5f, 16.0f)
        curveToRelative(1.61f, 0.0f, 3.09f, -0.59f, 4.23f, -1.57f)
        lineToRelative(0.27f, 0.28f)
        verticalLineToRelative(0.79f)
        lineToRelative(5.0f, 4.99f)
        lineTo(20.49f, 19.0f)
        lineToRelative(-4.99f, -5.0f)
        close()
        moveTo(9.5f, 14.0f)
        curveTo(7.01f, 14.0f, 5.0f, 11.99f, 5.0f, 9.5f)
        reflectiveCurveTo(7.01f, 5.0f, 9.5f, 5.0f)
        reflectiveCurveTo(14.0f, 7.01f, 14.0f, 9.5f)
        reflectiveCurveTo(11.99f, 14.0f, 9.5f, 14.0f)
        close()
    }
        }
    }

    val TouchApp: ImageVector by lazy {
        icon("TouchApp") {
    path(fill = Black) {
        moveTo(9.0f, 11.24f)
        verticalLineTo(7.5f)
        curveTo(9.0f, 6.12f, 10.12f, 5.0f, 11.5f, 5.0f)
        reflectiveCurveTo(14.0f, 6.12f, 14.0f, 7.5f)
        verticalLineToRelative(3.74f)
        curveToRelative(1.21f, -0.81f, 2.0f, -2.18f, 2.0f, -3.74f)
        curveTo(16.0f, 5.01f, 13.99f, 3.0f, 11.5f, 3.0f)
        reflectiveCurveTo(7.0f, 5.01f, 7.0f, 7.5f)
        curveTo(7.0f, 9.06f, 7.79f, 10.43f, 9.0f, 11.24f)
        close()
        moveTo(18.84f, 15.87f)
        lineToRelative(-4.54f, -2.26f)
        curveToRelative(-0.17f, -0.07f, -0.35f, -0.11f, -0.54f, -0.11f)
        horizontalLineTo(13.0f)
        verticalLineToRelative(-6.0f)
        curveTo(13.0f, 6.67f, 12.33f, 6.0f, 11.5f, 6.0f)
        reflectiveCurveTo(10.0f, 6.67f, 10.0f, 7.5f)
        verticalLineToRelative(10.74f)
        curveToRelative(-3.6f, -0.76f, -3.54f, -0.75f, -3.67f, -0.75f)
        curveToRelative(-0.31f, 0.0f, -0.59f, 0.13f, -0.79f, 0.33f)
        lineToRelative(-0.79f, 0.8f)
        lineToRelative(4.94f, 4.94f)
        curveTo(9.96f, 23.83f, 10.34f, 24.0f, 10.75f, 24.0f)
        horizontalLineToRelative(6.79f)
        curveToRelative(0.75f, 0.0f, 1.33f, -0.55f, 1.44f, -1.28f)
        lineToRelative(0.75f, -5.27f)
        curveToRelative(0.01f, -0.07f, 0.02f, -0.14f, 0.02f, -0.2f)
        curveTo(19.75f, 16.63f, 19.37f, 16.09f, 18.84f, 15.87f)
        close()
    }
        }
    }

    val CheckCircleOutline: ImageVector by lazy {
        icon("CheckCircleOutline") {
    path(fill = Black) {
        moveTo(12.0f, 2.0f)
        curveTo(6.48f, 2.0f, 2.0f, 6.48f, 2.0f, 12.0f)
        reflectiveCurveToRelative(4.48f, 10.0f, 10.0f, 10.0f)
        reflectiveCurveToRelative(10.0f, -4.48f, 10.0f, -10.0f)
        reflectiveCurveTo(17.52f, 2.0f, 12.0f, 2.0f)
        close()
        moveTo(12.0f, 20.0f)
        curveToRelative(-4.41f, 0.0f, -8.0f, -3.59f, -8.0f, -8.0f)
        reflectiveCurveToRelative(3.59f, -8.0f, 8.0f, -8.0f)
        reflectiveCurveToRelative(8.0f, 3.59f, 8.0f, 8.0f)
        reflectiveCurveToRelative(-3.59f, 8.0f, -8.0f, 8.0f)
        close()
        moveTo(16.59f, 7.58f)
        lineTo(10.0f, 14.17f)
        lineToRelative(-2.59f, -2.58f)
        lineTo(6.0f, 13.0f)
        lineToRelative(4.0f, 4.0f)
        lineToRelative(8.0f, -8.0f)
        close()
    }
        }
    }

    val Description: ImageVector by lazy {
        icon("Description") {
    path(fill = Black) {
        moveTo(8.0f, 16.0f)
        horizontalLineToRelative(8.0f)
        verticalLineToRelative(2.0f)
        lineTo(8.0f, 18.0f)
        close()
        moveTo(8.0f, 12.0f)
        horizontalLineToRelative(8.0f)
        verticalLineToRelative(2.0f)
        lineTo(8.0f, 14.0f)
        close()
        moveTo(14.0f, 2.0f)
        lineTo(6.0f, 2.0f)
        curveToRelative(-1.1f, 0.0f, -2.0f, 0.9f, -2.0f, 2.0f)
        verticalLineToRelative(16.0f)
        curveToRelative(0.0f, 1.1f, 0.89f, 2.0f, 1.99f, 2.0f)
        lineTo(18.0f, 22.0f)
        curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
        lineTo(20.0f, 8.0f)
        lineToRelative(-6.0f, -6.0f)
        close()
        moveTo(18.0f, 20.0f)
        lineTo(6.0f, 20.0f)
        lineTo(6.0f, 4.0f)
        horizontalLineToRelative(7.0f)
        verticalLineToRelative(5.0f)
        horizontalLineToRelative(5.0f)
        verticalLineToRelative(11.0f)
        close()
    }
        }
    }

    val PushPinOutline: ImageVector by lazy {
        icon("PushPinOutline") {
    path(fill = Black) {
        moveTo(14.0f, 4.0f)
        verticalLineToRelative(5.0f)
        curveToRelative(0.0f, 1.12f, 0.37f, 2.16f, 1.0f, 3.0f)
        horizontalLineTo(9.0f)
        curveToRelative(0.65f, -0.86f, 1.0f, -1.9f, 1.0f, -3.0f)
        verticalLineTo(4.0f)
        horizontalLineTo(14.0f)
        moveTo(17.0f, 2.0f)
        horizontalLineTo(7.0f)
        curveTo(6.45f, 2.0f, 6.0f, 2.45f, 6.0f, 3.0f)
        curveToRelative(0.0f, 0.55f, 0.45f, 1.0f, 1.0f, 1.0f)
        curveToRelative(0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f)
        lineToRelative(1.0f, 0.0f)
        verticalLineToRelative(5.0f)
        curveToRelative(0.0f, 1.66f, -1.34f, 3.0f, -3.0f, 3.0f)
        verticalLineToRelative(2.0f)
        horizontalLineToRelative(5.97f)
        verticalLineToRelative(7.0f)
        lineToRelative(1.0f, 1.0f)
        lineToRelative(1.0f, -1.0f)
        verticalLineToRelative(-7.0f)
        horizontalLineTo(19.0f)
        verticalLineToRelative(-2.0f)
        curveToRelative(0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f)
        curveToRelative(-1.66f, 0.0f, -3.0f, -1.34f, -3.0f, -3.0f)
        verticalLineTo(4.0f)
        lineToRelative(1.0f, 0.0f)
        curveToRelative(0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f)
        curveToRelative(0.55f, 0.0f, 1.0f, -0.45f, 1.0f, -1.0f)
        curveTo(18.0f, 2.45f, 17.55f, 2.0f, 17.0f, 2.0f)
        lineTo(17.0f, 2.0f)
        close()
    }
        }
    }

    val RadioButtonUnchecked: ImageVector by lazy {
        icon("RadioButtonUnchecked") {
    path(fill = Black) {
        moveTo(12.0f, 2.0f)
        curveTo(6.48f, 2.0f, 2.0f, 6.48f, 2.0f, 12.0f)
        reflectiveCurveToRelative(4.48f, 10.0f, 10.0f, 10.0f)
        reflectiveCurveToRelative(10.0f, -4.48f, 10.0f, -10.0f)
        reflectiveCurveTo(17.52f, 2.0f, 12.0f, 2.0f)
        close()
        moveTo(12.0f, 20.0f)
        curveToRelative(-4.42f, 0.0f, -8.0f, -3.58f, -8.0f, -8.0f)
        reflectiveCurveToRelative(3.58f, -8.0f, 8.0f, -8.0f)
        reflectiveCurveToRelative(8.0f, 3.58f, 8.0f, 8.0f)
        reflectiveCurveToRelative(-3.58f, 8.0f, -8.0f, 8.0f)
        close()
    }
        }
    }

    private val Black = SolidColor(Color.Black)

    private fun icon(
        name: String,
        autoMirror: Boolean = false,
        paths: ImageVector.Builder.() -> Unit,
    ): ImageVector = ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
        autoMirror = autoMirror,
    ).apply(paths).build()
}
