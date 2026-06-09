package com.stepanok.undp.designsystem.icons

import androidx.compose.ui.graphics.vector.ImageVector
import com.composables.icons.lucide.Activity
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.ArrowRight
import com.composables.icons.lucide.Award
import com.composables.icons.lucide.Bell
import com.composables.icons.lucide.Biohazard
import com.composables.icons.lucide.Bomb
import com.composables.icons.lucide.Building2
import com.composables.icons.lucide.Camera
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.ChevronDown
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.CloudOff
import com.composables.icons.lucide.CloudUpload
import com.composables.icons.lucide.Cross
import com.composables.icons.lucide.Download
import com.composables.icons.lucide.EllipsisVertical
import com.composables.icons.lucide.Eye
import com.composables.icons.lucide.Flag
import com.composables.icons.lucide.Flame
import com.composables.icons.lucide.House
import com.composables.icons.lucide.Image
import com.composables.icons.lucide.Info
import com.composables.icons.lucide.Languages
import com.composables.icons.lucide.Leaf
import com.composables.icons.lucide.List
import com.composables.icons.lucide.LocateFixed
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Map
import com.composables.icons.lucide.MapPin
import com.composables.icons.lucide.Medal
import com.composables.icons.lucide.Mic
import com.composables.icons.lucide.Pencil
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.RefreshCw
import com.composables.icons.lucide.Search
import com.composables.icons.lucide.Settings
import com.composables.icons.lucide.ShieldCheck
import com.composables.icons.lucide.Siren
import com.composables.icons.lucide.SlidersHorizontal
import com.composables.icons.lucide.Store
import com.composables.icons.lucide.Swords
import com.composables.icons.lucide.SwitchCamera
import com.composables.icons.lucide.TreePine
import com.composables.icons.lucide.TriangleAlert
import com.composables.icons.lucide.Truck
import com.composables.icons.lucide.User
import com.composables.icons.lucide.Waves
import com.composables.icons.lucide.Waypoints
import com.composables.icons.lucide.Wind
import com.composables.icons.lucide.X
import com.composables.icons.lucide.Zap

/**
 * Single indirection over the Lucide icon pack so screens never reference Lucide directly — one
 * place to retune a glyph or drop in a bundled SVG fallback. Names mirror the prototype's icon set.
 */
object BeaconIcons {
    // Navigation / common
    val Map: ImageVector get() = Lucide.Map
    val Reports: ImageVector get() = Lucide.List
    val Sync: ImageVector get() = Lucide.RefreshCw
    val Profile: ImageVector get() = Lucide.User
    val Plus: ImageVector get() = Lucide.Plus
    val ArrowRight: ImageVector get() = Lucide.ArrowRight
    val ArrowLeft: ImageVector get() = Lucide.ArrowLeft
    val ChevronRight: ImageVector get() = Lucide.ChevronRight
    val ChevronDown: ImageVector get() = Lucide.ChevronDown
    val Check: ImageVector get() = Lucide.Check
    val Close: ImageVector get() = Lucide.X
    val More: ImageVector get() = Lucide.EllipsisVertical
    val Search: ImageVector get() = Lucide.Search
    val Filter: ImageVector get() = Lucide.SlidersHorizontal
    val Info: ImageVector get() = Lucide.Info
    val Settings: ImageVector get() = Lucide.Settings
    val Edit: ImageVector get() = Lucide.Pencil
    val Download: ImageVector get() = Lucide.Download
    val Bell: ImageVector get() = Lucide.Bell
    val Language: ImageVector get() = Lucide.Languages

    // Location / map
    val Pin: ImageVector get() = Lucide.MapPin
    val Location: ImageVector get() = Lucide.LocateFixed

    // Offline / sync
    val CloudUp: ImageVector get() = Lucide.CloudUpload
    val CloudOff: ImageVector get() = Lucide.CloudOff

    // Capture / status
    val Camera: ImageVector get() = Lucide.Camera
    val FlipCamera: ImageVector get() = Lucide.SwitchCamera
    val Image: ImageVector get() = Lucide.Image
    val Flash: ImageVector get() = Lucide.Zap
    val Mic: ImageVector get() = Lucide.Mic
    val Shield: ImageVector get() = Lucide.ShieldCheck
    val Eye: ImageVector get() = Lucide.Eye
    val Warning: ImageVector get() = Lucide.TriangleAlert
    val Crisis: ImageVector get() = Lucide.Siren
    val Bolt: ImageVector get() = Lucide.Zap
    val Truck: ImageVector get() = Lucide.Truck

    // Infrastructure types
    val House: ImageVector get() = Lucide.House
    val Shop: ImageVector get() = Lucide.Store
    val Building: ImageVector get() = Lucide.Building2
    val Road: ImageVector get() = Lucide.Waypoints
    val Hospital: ImageVector get() = Lucide.Cross
    val Park: ImageVector get() = Lucide.TreePine

    // Crisis nature
    val Earthquake: ImageVector get() = Lucide.Activity
    val Flood: ImageVector get() = Lucide.Waves
    val Wind: ImageVector get() = Lucide.Wind
    val Flame: ImageVector get() = Lucide.Flame
    val Explosion: ImageVector get() = Lucide.Bomb
    val Chemical: ImageVector get() = Lucide.Biohazard
    val Conflict: ImageVector get() = Lucide.Swords
    val Flag: ImageVector get() = Lucide.Flag

    // Recognition / gamification
    val Award: ImageVector get() = Lucide.Award
    val Medal: ImageVector get() = Lucide.Medal
    val Leaf: ImageVector get() = Lucide.Leaf
}
