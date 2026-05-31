package net.astrorbits.football.client.config

import net.astrorbits.football.match.GoalConfig
import net.astrorbits.football.match.MatchConfig
import net.astrorbits.football.match.SpawnPosition
import net.astrorbits.football.match.TeamSpawnConfig
import net.astrorbits.football.network.MatchConfigApplyC2SPayload
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.components.StringWidget
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component

class MatchFieldConfigScreen(
    private val parent: Screen?,
    private val initial: MatchConfig,
) : Screen(TITLE) {

    // ---- goal A ----
    private var gaX1 = f(initial.goalA.x1); private var gaY1 = f(initial.goalA.y1); private var gaZ1 = f(initial.goalA.z1)
    private var gaX2 = f(initial.goalA.x2); private var gaY2 = f(initial.goalA.y2); private var gaZ2 = f(initial.goalA.z2)
    private var gaFx = f(initial.goalA.facingX); private var gaFy = f(initial.goalA.facingY); private var gaFz = f(initial.goalA.facingZ)

    // ---- goal B ----
    private var gbX1 = f(initial.goalB.x1); private var gbY1 = f(initial.goalB.y1); private var gbZ1 = f(initial.goalB.z1)
    private var gbX2 = f(initial.goalB.x2); private var gbY2 = f(initial.goalB.y2); private var gbZ2 = f(initial.goalB.z2)
    private var gbFx = f(initial.goalB.facingX); private var gbFy = f(initial.goalB.facingY); private var gbFz = f(initial.goalB.facingZ)

    // ---- spawn A ----
    private val saPlayers: MutableList<EditablePos> = initial.teamASpawn.players.map { EditablePos.from(it) }.toMutableList()
    private var saGkX = f(initial.teamASpawn.gk.x); private var saGkY = f(initial.teamASpawn.gk.y)
    private var saGkZ = f(initial.teamASpawn.gk.z); private var saGkYaw = f(initial.teamASpawn.gk.yaw.toDouble())
    private var saGkPit = f(initial.teamASpawn.gk.pitch.toDouble())
    private var saIdx = 0

    // ---- spawn B ----
    private val sbPlayers: MutableList<EditablePos> = initial.teamBSpawn.players.map { EditablePos.from(it) }.toMutableList()
    private var sbGkX = f(initial.teamBSpawn.gk.x); private var sbGkY = f(initial.teamBSpawn.gk.y)
    private var sbGkZ = f(initial.teamBSpawn.gk.z); private var sbGkYaw = f(initial.teamBSpawn.gk.yaw.toDouble())
    private var sbGkPit = f(initial.teamBSpawn.gk.pitch.toDouble())
    private var sbIdx = 0

    private var currentTab = 0

    // layout
    private val LX = 10; private val IX = 72  // left label, input
    private val RX = 178; private val SX = 240 // right label, input
    private val LW = 58; private val IW = 86   // label/input widths
    private val RH = 21; private val EH = 20   // row height, edit height
    private val SY = 48

    override fun init() { buildTabBar(); buildCurrentTab(); buildBottomButtons() }

    private fun buildTabBar() {
        val tw = 70; val th = 20; val total = tw * 4 + 6; val sx = (width - total) / 2
        for (i in 0 until 4) {
            val b = Button.builder(TABS[i]) { switchTab(i) }.bounds(sx + i * (tw + 2), 28, tw, th).build()
            b.active = i != currentTab; addRenderableWidget(b)
        }
    }

    private fun buildCurrentTab() {
        var y = SY
        when (currentTab) {
            0 -> { // Goal A
                r(y, L_GA_X1, { gaX1 }, { gaX1 = it }, L_GA_Y1, { gaY1 }, { gaY1 = it }); y += RH
                r(y, L_GA_Z1, { gaZ1 }, { gaZ1 = it }, L_GA_X2, { gaX2 }, { gaX2 = it }); y += RH
                r(y, L_GA_Y2, { gaY2 }, { gaY2 = it }, L_GA_Z2, { gaZ2 }, { gaZ2 = it }); y += RH
                r(y, L_GA_FX, { gaFx }, { gaFx = it }, L_GA_FY, { gaFy }, { gaFy = it }); y += RH
                r(y, L_GA_FZ, { gaFz }, { gaFz = it }, null, null, null); y += RH
                addBtn(y, IX, CORNER1) { setCorner1(); rebuild() }; addBtn(y, SX, CORNER2) { setCorner2(); rebuild() }
            }
            1 -> { // Goal B
                r(y, L_GB_X1, { gbX1 }, { gbX1 = it }, L_GB_Y1, { gbY1 }, { gbY1 = it }); y += RH
                r(y, L_GB_Z1, { gbZ1 }, { gbZ1 = it }, L_GB_X2, { gbX2 }, { gbX2 = it }); y += RH
                r(y, L_GB_Y2, { gbY2 }, { gbY2 = it }, L_GB_Z2, { gbZ2 }, { gbZ2 = it }); y += RH
                r(y, L_GB_FX, { gbFx }, { gbFx = it }, L_GB_FY, { gbFy }, { gbFy = it }); y += RH
                r(y, L_GB_FZ, { gbFz }, { gbFz = it }, null, null, null); y += RH
                addBtn(y, IX, CORNER1) { setCorner1b(); rebuild() }; addBtn(y, SX, CORNER2) { setCorner2b(); rebuild() }
            }
            2 -> buildSpawnTab(
                y,
                { saGkX }, { saGkY }, { saGkZ }, { saGkYaw }, { saGkPit },
                { v -> saGkX = v }, { v -> saGkY = v }, { v -> saGkZ = v }, { v -> saGkYaw = v }, { v -> saGkPit = v },
                saPlayers, { saIdx }, { saIdx = it },
                L_SA_X, L_SA_Y, L_SA_Z, L_SA_YW, L_SA_PT,
            ) { fillGkA(); rebuild() }
            3 -> buildSpawnTab(
                y,
                { sbGkX }, { sbGkY }, { sbGkZ }, { sbGkYaw }, { sbGkPit },
                { v -> sbGkX = v }, { v -> sbGkY = v }, { v -> sbGkZ = v }, { v -> sbGkYaw = v }, { v -> sbGkPit = v },
                sbPlayers, { sbIdx }, { sbIdx = it },
                L_SB_X, L_SB_Y, L_SB_Z, L_SB_YW, L_SB_PT,
            ) { fillGkB(); rebuild() }
        }
    }

    /** Build a spawn tab: GK section + player positions with add/remove/nav. */
    private fun buildSpawnTab(
        startY: Int,
        gkx: () -> String, gky: () -> String, gkz: () -> String, gkyw: () -> String, gkpt: () -> String,
        sgkx: (String) -> Unit, sgky: (String) -> Unit, sgkz: (String) -> Unit, sgkyw: (String) -> Unit, sgkpt: (String) -> Unit,
        players: MutableList<EditablePos>, idx: () -> Int, setIdx: (Int) -> Unit,
        lx: Component, ly: Component, lz: Component, lyw: Component, lpt: Component,
        onUsePos: () -> Unit,
    ) {
        var y = startY

        // ---- GK section ----
        addRenderableWidget(StringWidget(LX, y, 120, EH, GK_HEADER, font)); y += RH
        r(y, lx, gkx, sgkx, ly, gky, sgky); y += RH
        r(y, lz, gkz, sgkz, lyw, gkyw, sgkyw); y += RH
        r(y, lpt, gkpt, sgkpt, null, null, null)
        addBtn(y, IX, USE_POS, onUsePos)
        y += RH + 4

        // ---- player positions ----
        addRenderableWidget(StringWidget(LX, y, 200, EH, PLR_HEADER, font)); y += RH

        if (players.isEmpty()) {
            addRenderableWidget(Button.builder(ADD_POS) {
                players.add(EditablePos("0", "64", "0", "0", "0")); rebuild()
            }.bounds(IX, y, 86, EH).build())
        } else {
            // nav row
            val i = idx()
            val prevBtn = Button.builder(Component.literal("<")) {
                saveAndNavigate(players, i, (i - 1 + players.size) % players.size, setIdx)
            }.bounds(IX, y, 20, EH).build()
            val nextBtn = Button.builder(Component.literal(">")) {
                saveAndNavigate(players, i, (i + 1) % players.size, setIdx)
            }.bounds(IX + 22, y, 20, EH).build()
            val addBtn = Button.builder(ADD_POS) {
                players.add(EditablePos("0", "64", "0", "0", "0")); rebuild()
            }.bounds(IX + 44, y, 40, EH).build()
            addRenderableWidget(prevBtn)
            addRenderableWidget(nextBtn)
            addRenderableWidget(addBtn)
            addRenderableWidget(StringWidget(IX + 90, y, 60, EH,
                Component.literal("${i + 1}/${players.size}"), font))
            if (players.size > 1) {
                addRenderableWidget(Button.builder(DEL_POS) {
                    players.removeAt(i)
                    if (i >= players.size) setIdx(players.size - 1)
                    rebuildWidgets()
                }.bounds(SX, y, 60, EH).build())
            }
            y += RH

            // fields for current position (sync from list)
            val p = players[i]
            plX = p.x; plY = p.y; plZ = p.z; plYw = p.yaw; plPt = p.pitch
            r(y, lx, { plX }, { plX = it }, ly, { plY }, { plY = it }); y += RH
            r(y, lz, { plZ }, { plZ = it }, lyw, { plYw }, { plYw = it }); y += RH
            r(y, lpt, { plPt }, { plPt = it }, null, null, null)
            addBtn(y, IX, USE_POS) { fillCurPlayer(); rebuild() }
        }
    }

    // temp strings for the currently-edited player position
    private var plX = ""; private var plY = ""; private var plZ = ""
    private var plYw = ""; private var plPt = ""

    private fun flushPlayerPos(list: MutableList<EditablePos>, i: Int,
                               x: String, y: String, z: String, yaw: String, pit: String) {
        if (i in list.indices) list[i] = EditablePos(x, y, z, yaw, pit)
    }

    // ---- widgets ----
    private fun r(y: Int, l1: Component, g1: () -> String, s1: (String) -> Unit,
                  l2: Component?, g2: (() -> String)?, s2: ((String) -> Unit)?) {
        addLabel(l1, LX, y)
        val e1 = EditBox(font, IX, y, IW, EH, l1); e1.setValue(g1()); e1.setResponder { s1(it) }; addRenderableWidget(e1)
        if (l2 != null && g2 != null && s2 != null) {
            addLabel(l2, RX, y)
            val e2 = EditBox(font, SX, y, IW, EH, l2); e2.setValue(g2()); e2.setResponder { s2(it) }; addRenderableWidget(e2)
        }
    }
    private fun addLabel(t: Component, x: Int, y: Int) { addRenderableWidget(StringWidget(x, y, LW, EH, t, font)) }
    private fun addBtn(y: Int, x: Int, label: Component, action: () -> Unit) {
        addRenderableWidget(Button.builder(label, { action() }).bounds(x, y, IW, EH).build())
    }

    // ---- corner buttons ----
    private fun setCorner1()  { withPos { x, y, z -> gaX1 = x; gaY1 = y; gaZ1 = z } }
    private fun setCorner2()  { withPos { x, y, z -> gaX2 = x; gaY2 = y; gaZ2 = z } }
    private fun setCorner1b() { withPos { x, y, z -> gbX1 = x; gbY1 = y; gbZ1 = z } }
    private fun setCorner2b() { withPos { x, y, z -> gbX2 = x; gbY2 = y; gbZ2 = z } }

    private fun fillGkA() { withRot { x, y, z, yw, pt -> saGkX = x; saGkY = y; saGkZ = z; saGkYaw = yw; saGkPit = pt } }
    private fun fillGkB() { withRot { x, y, z, yw, pt -> sbGkX = x; sbGkY = y; sbGkZ = z; sbGkYaw = yw; sbGkPit = pt } }
    private fun fillCurPlayer() { withRot { x, y, z, yw, pt -> plX = x; plY = y; plZ = z; plYw = yw; plPt = pt } }

    private fun withPos(f: (String, String, String) -> Unit) { val p = mc.player!!; f(f(p.x), f(p.y), f(p.z)) }
    private fun withRot(f: (String, String, String, String, String) -> Unit) {
        val p = mc.player!!; f(f(p.x), f(p.y), f(p.z), f(p.yRot.toDouble()), f(p.xRot.toDouble()))
    }
    private val mc get() = minecraft!!

    private fun buildBottomButtons() {
        val by = height - 50
        addRenderableWidget(Button.builder(SAVE) { doSave() }.bounds(width / 2 - 102, by, 100, 20).build())
        addRenderableWidget(Button.builder(CANCEL) { onClose() }.bounds(width / 2 + 2, by, 100, 20).build())
    }

    private fun switchTab(i: Int) {
        // flush current player pos before switching
        if (currentTab == 2) flushPlayerPos(saPlayers, saIdx, plX, plY, plZ, plYw, plPt)
        if (currentTab == 3) flushPlayerPos(sbPlayers, sbIdx, plX, plY, plZ, plYw, plPt)
        if (i != currentTab) { currentTab = i; rebuildWidgets() }
    }

    private fun rebuild() { rebuildWidgets() }

    private fun saveAndNavigate(players: MutableList<EditablePos>, oldIdx: Int, newIdx: Int, setIdx: (Int) -> Unit) {
        flushPlayerPos(players, oldIdx, plX, plY, plZ, plYw, plPt)
        setIdx(newIdx)
        rebuildWidgets()
    }

    override fun onClose() { mc.setScreen(parent) }
    override fun isPauseScreen() = false

    private fun doSave() {
        // flush current edits
        if (currentTab == 2) flushPlayerPos(saPlayers, saIdx, plX, plY, plZ, plYw, plPt)
        if (currentTab == 3) flushPlayerPos(sbPlayers, sbIdx, plX, plY, plZ, plYw, plPt)

        val cfg = MatchConfig(
            teamAName = initial.teamAName, teamBName = initial.teamBName,
            halfTimeMinutes = initial.halfTimeMinutes,
            enableStoppageTime = initial.enableStoppageTime,
            stoppageTimeMaxMinutes = initial.stoppageTimeMaxMinutes,
            enableExtraTime = initial.enableExtraTime,
            extraTimeHalfMinutes = initial.extraTimeHalfMinutes,
            enablePenaltyShootout = initial.enablePenaltyShootout,
            goalA = mkGoal(gaX1, gaY1, gaZ1, gaX2, gaY2, gaZ2, gaFx, gaFy, gaFz, initial.goalA),
            goalB = mkGoal(gbX1, gbY1, gbZ1, gbX2, gbY2, gbZ2, gbFx, gbFy, gbFz, initial.goalB),
            teamASpawn = mkSpawn(saGkX, saGkY, saGkZ, saGkYaw, saGkPit, saPlayers, initial.teamASpawn),
            teamBSpawn = mkSpawn(sbGkX, sbGkY, sbGkZ, sbGkYaw, sbGkPit, sbPlayers, initial.teamBSpawn),
        )
        if (ClientPlayNetworking.canSend(MatchConfigApplyC2SPayload.TYPE))
            ClientPlayNetworking.send(MatchConfigApplyC2SPayload(cfg))
        onClose()
    }

    private fun mkGoal(x1: String, y1: String, z1: String, x2: String, y2: String, z2: String,
                       fx: String, fy: String, fz: String, fb: GoalConfig) = GoalConfig(
        x1 = x1.toD(fb.x1), y1 = y1.toD(fb.y1), z1 = z1.toD(fb.z1),
        x2 = x2.toD(fb.x2), y2 = y2.toD(fb.y2), z2 = z2.toD(fb.z2),
        facingX = fx.toD(fb.facingX), facingY = fy.toD(fb.facingY), facingZ = fz.toD(fb.facingZ),
    )

    private fun mkSpawn(gx: String, gy: String, gz: String, gyaw: String, gpit: String,
                        pl: List<EditablePos>, fb: TeamSpawnConfig) = TeamSpawnConfig(
        gk = SpawnPosition(gx.toD(fb.gk.x), gy.toD(fb.gk.y), gz.toD(fb.gk.z), gyaw.toF(fb.gk.yaw), gpit.toF(fb.gk.pitch)),
        players = pl.map { SpawnPosition(it.x.toD(0.0), it.y.toD(64.0), it.z.toD(0.0), it.yaw.toF(0f), it.pitch.toF(0f)) },
    )

    private fun String.toD(fb: Double) = toDoubleOrNull() ?: fb
    private fun String.toF(fb: Float) = toFloatOrNull() ?: fb
    private fun f(v: Double) = "%.1f".format(v)

    data class EditablePos(var x: String, var y: String, var z: String, var yaw: String, var pitch: String) {
        companion object {
            fun from(sp: SpawnPosition) = EditablePos(f(sp.x), f(sp.y), f(sp.z), f(sp.yaw.toDouble()), f(sp.pitch.toDouble()))
            private fun f(v: Double) = "%.1f".format(v)
        }
    }

    companion object {
        private val TITLE  = Component.translatable("screen.nmbct-football.field.title")
        private val SAVE   = Component.translatable("screen.nmbct-football.match.save")
        private val CANCEL = Component.translatable("screen.nmbct-football.match.cancel")
        private val CORNER1 = Component.translatable("screen.nmbct-football.field.set_corner1")
        private val CORNER2 = Component.translatable("screen.nmbct-football.field.set_corner2")
        private val USE_POS = Component.translatable("screen.nmbct-football.field.use_current_pos")
        private val ADD_POS = Component.translatable("screen.nmbct-football.field.add_pos")
        private val DEL_POS = Component.translatable("screen.nmbct-football.field.del_pos")
        private val GK_HEADER  = Component.translatable("screen.nmbct-football.field.gk_header")
        private val PLR_HEADER = Component.translatable("screen.nmbct-football.field.plr_header")

        private val TAB_A = Component.translatable("screen.nmbct-football.field.tab.goal_a")
        private val TAB_B = Component.translatable("screen.nmbct-football.field.tab.goal_b")
        private val TAB_SA = Component.translatable("screen.nmbct-football.field.tab.spawn_a")
        private val TAB_SB = Component.translatable("screen.nmbct-football.field.tab.spawn_b")
        private val TABS = arrayOf(TAB_A, TAB_B, TAB_SA, TAB_SB)

        // goal A
        private val L_GA_X1 = Component.translatable("screen.nmbct-football.field.goal_a.x1")
        private val L_GA_Y1 = Component.translatable("screen.nmbct-football.field.goal_a.y1")
        private val L_GA_Z1 = Component.translatable("screen.nmbct-football.field.goal_a.z1")
        private val L_GA_X2 = Component.translatable("screen.nmbct-football.field.goal_a.x2")
        private val L_GA_Y2 = Component.translatable("screen.nmbct-football.field.goal_a.y2")
        private val L_GA_Z2 = Component.translatable("screen.nmbct-football.field.goal_a.z2")
        private val L_GA_FX = Component.translatable("screen.nmbct-football.field.goal_a.fx")
        private val L_GA_FY = Component.translatable("screen.nmbct-football.field.goal_a.fy")
        private val L_GA_FZ = Component.translatable("screen.nmbct-football.field.goal_a.fz")
        // goal B
        private val L_GB_X1 = Component.translatable("screen.nmbct-football.field.goal_b.x1")
        private val L_GB_Y1 = Component.translatable("screen.nmbct-football.field.goal_b.y1")
        private val L_GB_Z1 = Component.translatable("screen.nmbct-football.field.goal_b.z1")
        private val L_GB_X2 = Component.translatable("screen.nmbct-football.field.goal_b.x2")
        private val L_GB_Y2 = Component.translatable("screen.nmbct-football.field.goal_b.y2")
        private val L_GB_Z2 = Component.translatable("screen.nmbct-football.field.goal_b.z2")
        private val L_GB_FX = Component.translatable("screen.nmbct-football.field.goal_b.fx")
        private val L_GB_FY = Component.translatable("screen.nmbct-football.field.goal_b.fy")
        private val L_GB_FZ = Component.translatable("screen.nmbct-football.field.goal_b.fz")
        // spawn A
        private val L_SA_X  = Component.translatable("screen.nmbct-football.field.spawn_a.gk_x")
        private val L_SA_Y  = Component.translatable("screen.nmbct-football.field.spawn_a.gk_y")
        private val L_SA_Z  = Component.translatable("screen.nmbct-football.field.spawn_a.gk_z")
        private val L_SA_YW = Component.translatable("screen.nmbct-football.field.spawn_a.gk_yaw")
        private val L_SA_PT = Component.translatable("screen.nmbct-football.field.spawn_a.gk_pitch")
        // spawn B
        private val L_SB_X  = Component.translatable("screen.nmbct-football.field.spawn_b.gk_x")
        private val L_SB_Y  = Component.translatable("screen.nmbct-football.field.spawn_b.gk_y")
        private val L_SB_Z  = Component.translatable("screen.nmbct-football.field.spawn_b.gk_z")
        private val L_SB_YW = Component.translatable("screen.nmbct-football.field.spawn_b.gk_yaw")
        private val L_SB_PT = Component.translatable("screen.nmbct-football.field.spawn_b.gk_pitch")
    }
}
