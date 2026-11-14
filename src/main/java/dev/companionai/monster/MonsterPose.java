package dev.companionai.monster;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.EnumMap;

/**
 * Esqueleto do monstro (só matemática, sem Bukkit).
 * Coordenadas locais em blocos: +Y para cima, +Z para frente, origem no chão.
 * Cada parte é um bloco 1x1x1 esticado/girado por uma matriz.
 */
public final class MonsterPose {

    /** "Pele" de cada parte; o MonsterRig troca por blocos do Minecraft. */
    public enum Skin {
        SKIN,        // pele pálida granulada
        LIMB,        // pele das pernas (um pouco acinzentada)
        BONE,        // osso aparente (costelas, vértebras, joelhos)
        TOOTH,       // dentes
        DARK,        // buracos, garras, nariz
        BLOOD,       // sangue fresco
        DRIED_BLOOD, // sangue seco
        GUMS,        // carne / gengiva
        EYE          // pupila vermelha que brilha no escuro
    }

    public enum Part {
        // Corpo
        SPINE(Skin.SKIN), CHEST(Skin.SKIN), PELVIS(Skin.SKIN),
        VERT_1(Skin.BONE), VERT_2(Skin.BONE), VERT_3(Skin.BONE), VERT_4(Skin.BONE), VERT_5(Skin.BONE), VERT_6(Skin.BONE),
        SCAPULA_L(Skin.BONE), SCAPULA_R(Skin.BONE), HIPBONE_L(Skin.BONE), HIPBONE_R(Skin.BONE),
        RIB_L1(Skin.BONE), RIB_R1(Skin.BONE), RIB_L2(Skin.BONE), RIB_R2(Skin.BONE),
        RIB_L3(Skin.BONE), RIB_R3(Skin.BONE), RIB_L4(Skin.BONE), RIB_R4(Skin.BONE),
        CHEST_BLOOD(Skin.BLOOD), BELLY_BLOOD(Skin.DRIED_BLOOD), FLANK_BLOOD(Skin.DRIED_BLOOD),
        // Pernas
        LEG_FL_U(Skin.LIMB), LEG_FL_L(Skin.LIMB), KNEE_FL(Skin.BONE), PAW_FL(Skin.LIMB), CLAW_FL(Skin.DARK),
        LEG_FR_U(Skin.LIMB), LEG_FR_L(Skin.LIMB), KNEE_FR(Skin.BONE), PAW_FR(Skin.LIMB), CLAW_FR(Skin.DARK),
        LEG_BL_U(Skin.LIMB), LEG_BL_L(Skin.LIMB), KNEE_BL(Skin.BONE), PAW_BL(Skin.LIMB), CLAW_BL(Skin.DARK),
        LEG_BR_U(Skin.LIMB), LEG_BR_L(Skin.LIMB), KNEE_BR(Skin.BONE), PAW_BR(Skin.LIMB), CLAW_BR(Skin.DARK),
        LEG_BLOOD(Skin.BLOOD), PAW_BLOOD(Skin.DRIED_BLOOD),
        // Rabo
        TAIL_1(Skin.SKIN), TAIL_2(Skin.LIMB),
        // Pescoço e cabeça (escondidos para quem controla, senão tapam a câmera)
        NECK_1(Skin.SKIN, true), NECK_2(Skin.SKIN, true), NECK_BLOOD(Skin.BLOOD, true),
        CRANIUM(Skin.SKIN, true), SNOUT(Skin.SKIN, true), NOSE(Skin.DARK, true),
        SOCKET_L(Skin.DARK, true), SOCKET_R(Skin.DARK, true), EYE_L(Skin.EYE, true), EYE_R(Skin.EYE, true),
        SNOUT_BLOOD(Skin.BLOOD, true), LIP_BLOOD(Skin.BLOOD, true),
        TOOTH_U1(Skin.TOOTH, true), TOOTH_U2(Skin.TOOTH, true), TOOTH_U3(Skin.TOOTH, true), TOOTH_U4(Skin.TOOTH, true),
        JAW(Skin.GUMS, true), JAW_BLOOD(Skin.BLOOD, true),
        TOOTH_L1(Skin.TOOTH, true), TOOTH_L2(Skin.TOOTH, true), TOOTH_L3(Skin.TOOTH, true),
        DRIP_1(Skin.BLOOD, true), DRIP_2(Skin.BLOOD, true), DRIP_3(Skin.BLOOD, true),
        EAR_L(Skin.SKIN, true), EAR_R(Skin.SKIN, true);

        public final Skin skin;
        /** Escondido para quem controla (senão tapa a câmera). */
        public final boolean head;

        Part(Skin skin) { this(skin, false); }

        Part(Skin skin, boolean head) {
            this.skin = skin;
            this.head = head;
        }
    }

    /** Resultado: matriz de cada parte + ponto da boca (para o sangue pingar). */
    public record Result(EnumMap<Part, Matrix4f> parts, Vector3f mouth, Vector3f[] dripTips) {}

    static final float H = 1.97f;
    static final float W_LEG = 0.12f;

    private MonsterPose() {}

    /**
     * @param phase     fase da caminhada (radianos)
     * @param amp       intensidade da passada (0 = parado)
     * @param headPitch inclinação da cabeça (radianos, + = para baixo)
     * @param headYaw   giro da cabeça (tique nervoso)
     * @param headRoll  cabeça torta (tique nervoso)
     * @param jawOpen   abertura da boca (radianos)
     * @param time      tempo em ticks (respiração, sangue pingando, balanço)
     */
    public static Result compute(double phase, double amp, double headPitch, double headYaw,
                                 double headRoll, double jawOpen, double time) {
        EnumMap<Part, Matrix4f> out = new EnumMap<>(Part.class);
        float a = (float) amp;
        float bob = (float) (-0.05 * amp * Math.abs(Math.sin(phase)));
        float h = H + bob;
        float breath = (float) (Math.sin(time * 0.09) * 0.025);

        // ---------------- Corpo
        out.put(Part.SPINE, box(new Matrix4f(), -0.13f, h - 0.05f, -1.95f, 0.26f, 0.20f, 1.70f));
        out.put(Part.CHEST, box(new Matrix4f(), -0.17f - breath, h - 0.30f - breath, -0.88f, 0.34f + 2 * breath, 0.42f + breath, 0.60f));
        out.put(Part.PELVIS, box(new Matrix4f(), -0.16f, h - 0.12f, -1.92f, 0.32f, 0.28f, 0.36f));

        Part[] verts = {Part.VERT_1, Part.VERT_2, Part.VERT_3, Part.VERT_4, Part.VERT_5, Part.VERT_6};
        for (int i = 0; i < verts.length; i++) {
            float z = -1.82f + i * 0.27f;
            out.put(verts[i], box(new Matrix4f(), -0.05f, h + 0.14f, z, 0.10f, 0.07f, 0.11f));
        }
        out.put(Part.SCAPULA_L, box(new Matrix4f().translate(0.15f, h + 0.08f, -0.62f).rotateZ(-0.35f), -0.05f, 0, -0.14f, 0.10f, 0.06f, 0.28f));
        out.put(Part.SCAPULA_R, box(new Matrix4f().translate(-0.15f, h + 0.08f, -0.62f).rotateZ(0.35f), -0.05f, 0, -0.14f, 0.10f, 0.06f, 0.28f));
        out.put(Part.HIPBONE_L, box(new Matrix4f(), 0.11f, h + 0.08f, -1.84f, 0.10f, 0.10f, 0.13f));
        out.put(Part.HIPBONE_R, box(new Matrix4f(), -0.21f, h + 0.08f, -1.84f, 0.10f, 0.10f, 0.13f));

        // Costelas penduradas na barriga (respiram junto)
        float[] ribZ = {-0.98f, -1.16f, -1.34f, -1.52f};
        Part[][] ribs = {{Part.RIB_L1, Part.RIB_R1}, {Part.RIB_L2, Part.RIB_R2}, {Part.RIB_L3, Part.RIB_R3}, {Part.RIB_L4, Part.RIB_R4}};
        for (int i = 0; i < ribs.length; i++) {
            for (int side = 0; side < 2; side++) {
                float sx = side == 0 ? 1f : -1f;
                Matrix4f m = new Matrix4f().translate(sx * 0.11f, h, ribZ[i]).rotateZ(sx * (0.38f + breath * 2));
                out.put(ribs[i][side], segDown(m, 0.045f, 0.44f - i * 0.06f));
            }
        }

        // Sangue no corpo
        out.put(Part.CHEST_BLOOD, box(new Matrix4f(), -0.11f, h - 0.24f, -0.285f, 0.22f, 0.20f, 0.012f));
        out.put(Part.BELLY_BLOOD, box(new Matrix4f(), -0.12f, h - 0.312f, -0.62f, 0.24f, 0.012f, 0.30f));
        out.put(Part.FLANK_BLOOD, box(new Matrix4f(), 0.131f, h - 0.02f, -1.45f, 0.012f, 0.13f, 0.38f));

        // ---------------- Pernas (trote: diagonais juntas)
        double p0 = phase;
        double p1 = phase + Math.PI;
        Matrix4f flKnee = leg(out, Part.LEG_FL_U, Part.LEG_FL_L, Part.KNEE_FL, Part.PAW_FL, Part.CLAW_FL, 0.19f, h, -0.50f, 1.00f, 1.00f, true, p0, a);
        leg(out, Part.LEG_FR_U, Part.LEG_FR_L, Part.KNEE_FR, Part.PAW_FR, Part.CLAW_FR, -0.19f, h, -0.50f, 1.00f, 1.00f, true, p1, a);
        leg(out, Part.LEG_BL_U, Part.LEG_BL_L, Part.KNEE_BL, Part.PAW_BL, Part.CLAW_BL, 0.19f, h, -1.75f, 1.05f, 1.05f, false, p1, a);
        Matrix4f brKnee = leg(out, Part.LEG_BR_U, Part.LEG_BR_L, Part.KNEE_BR, Part.PAW_BR, Part.CLAW_BR, -0.19f, h, -1.75f, 1.05f, 1.05f, false, p0, a);
        // Sangue escorrendo na canela da frente e na pata de trás
        out.put(Part.LEG_BLOOD, box(new Matrix4f(flKnee), -0.052f, -0.62f, 0.053f, 0.104f, 0.34f, 0.01f));
        out.put(Part.PAW_BLOOD, box(new Matrix4f(brKnee).translate(0, -1.05f, 0), -0.055f, -0.02f, -0.06f, 0.11f, 0.02f, 0.12f));

        // ---------------- Rabo (2 partes)
        float sway = (float) (Math.sin(time * 0.15) * 0.25);
        Matrix4f tail = new Matrix4f().translate(0, h + 0.05f, -1.92f)
                .rotateY(sway).rotateX(0.6f + (float) (0.1 * Math.sin(phase * 0.5)));
        out.put(Part.TAIL_1, segDown(new Matrix4f(tail), 0.08f, 0.42f));
        Matrix4f tail2 = new Matrix4f(tail).translate(0, -0.42f, 0).rotateY(sway * 0.8f).rotateX(0.35f);
        out.put(Part.TAIL_2, segDown(tail2, 0.055f, 0.42f));

        // ---------------- Pescoço em S (2 partes) que balança ao andar
        float neckSway = (float) (0.06 * amp * Math.sin(phase * 2));
        Matrix4f neck1 = new Matrix4f().translate(0, h + 0.06f, -0.42f).rotateZ(neckSway).rotateX(0.85f);
        out.put(Part.NECK_1, segUp(neck1, 0.15f, 0.52f));
        Matrix4f neck2 = new Matrix4f(neck1).translate(0, 0.50f, 0).rotateX(-0.55f);
        out.put(Part.NECK_2, segUp(neck2, 0.13f, 0.48f));
        out.put(Part.NECK_BLOOD, box(new Matrix4f(neck1), -0.06f, 0.05f, 0.076f, 0.12f, 0.40f, 0.01f));

        // ---------------- Cabeça na ponta do pescoço
        Matrix4f head = new Matrix4f(neck2).translate(0, 0.48f, 0).rotateX(-0.30f)
                .rotateY((float) headYaw).rotateX((float) headPitch).rotateZ((float) headRoll);
        out.put(Part.CRANIUM, box(new Matrix4f(head), -0.155f, -0.12f, -0.17f, 0.31f, 0.28f, 0.34f));
        out.put(Part.SNOUT, box(new Matrix4f(head), -0.10f, -0.12f, 0.15f, 0.20f, 0.17f, 0.42f));
        out.put(Part.NOSE, box(new Matrix4f(head), -0.045f, -0.02f, 0.565f, 0.09f, 0.06f, 0.02f));
        out.put(Part.SOCKET_L, box(new Matrix4f(head), 0.150f, -0.01f, 0.03f, 0.012f, 0.10f, 0.12f));
        out.put(Part.SOCKET_R, box(new Matrix4f(head), -0.162f, -0.01f, 0.03f, 0.012f, 0.10f, 0.12f));
        out.put(Part.EYE_L, box(new Matrix4f(head), 0.158f, 0.02f, 0.065f, 0.01f, 0.04f, 0.045f));
        out.put(Part.EYE_R, box(new Matrix4f(head), -0.168f, 0.02f, 0.065f, 0.01f, 0.04f, 0.045f));
        out.put(Part.SNOUT_BLOOD, box(new Matrix4f(head), -0.095f, -0.126f, 0.24f, 0.19f, 0.01f, 0.32f));
        out.put(Part.LIP_BLOOD, box(new Matrix4f(head), -0.101f, -0.12f, 0.38f, 0.202f, 0.07f, 0.19f));

        float[][] upperTeeth = {{0.07f, 0.50f}, {-0.07f, 0.50f}, {0.075f, 0.36f}, {-0.075f, 0.36f}};
        Part[] upper = {Part.TOOTH_U1, Part.TOOTH_U2, Part.TOOTH_U3, Part.TOOTH_U4};
        for (int i = 0; i < upper.length; i++) {
            float len = i < 2 ? 0.10f : 0.07f; // presas da frente maiores
            out.put(upper[i], box(new Matrix4f(head), upperTeeth[i][0] - 0.016f, -0.12f - len, upperTeeth[i][1], 0.032f, len, 0.032f));
        }

        // Mandíbula (gengiva vermelha) com dentes e sangue
        Matrix4f jaw = new Matrix4f(head).translate(0, -0.12f, 0.10f).rotateX((float) jawOpen);
        out.put(Part.JAW, box(new Matrix4f(jaw), -0.09f, -0.07f, 0.0f, 0.18f, 0.07f, 0.44f));
        out.put(Part.JAW_BLOOD, box(new Matrix4f(jaw), -0.085f, -0.076f, 0.05f, 0.17f, 0.01f, 0.36f));
        float[][] lowerTeeth = {{0.055f, 0.36f}, {-0.055f, 0.36f}, {0.0f, 0.40f}};
        Part[] lower = {Part.TOOTH_L1, Part.TOOTH_L2, Part.TOOTH_L3};
        for (int i = 0; i < lower.length; i++) {
            out.put(lower[i], box(new Matrix4f(jaw), lowerTeeth[i][0] - 0.015f, 0.0f, lowerTeeth[i][1], 0.03f, 0.06f, 0.03f));
        }

        // Sangue pingando da boca: fios que crescem e "caem"
        Part[] drips = {Part.DRIP_1, Part.DRIP_2, Part.DRIP_3};
        float[][] dripPos = {{0.05f, 0.38f}, {-0.04f, 0.30f}, {0.0f, 0.20f}};
        double[] dripSpeed = {0.021, 0.016, 0.027};
        Vector3f[] tips = new Vector3f[drips.length];
        for (int i = 0; i < drips.length; i++) {
            double t = (time * dripSpeed[i] + i * 0.37) % 1.0;
            float len = (float) (0.04 + 0.34 * t * t);
            // Fio sempre na vertical (gravidade), preso embaixo da mandíbula
            Vector3f start = new Matrix4f(jaw).transformPosition(new Vector3f(dripPos[i][0], -0.07f, dripPos[i][1]));
            tips[i] = new Vector3f(start.x, start.y - len, start.z);
            out.put(drips[i], segDown(new Matrix4f().translate(start), 0.022f, len));
        }

        // Orelhas compridas para trás (uma rasgada, mais curta)
        for (int side = 0; side < 2; side++) {
            float sx = side == 0 ? 1f : -1f;
            Matrix4f ear = new Matrix4f(head).translate(sx * 0.10f, 0.14f, -0.10f)
                    .rotateZ(-sx * 0.22f).rotateX(-1.05f);
            out.put(side == 0 ? Part.EAR_L : Part.EAR_R, segUp(ear, 0.055f, side == 0 ? 0.52f : 0.34f));
        }

        Vector3f mouth = new Matrix4f(jaw).transformPosition(new Vector3f(0, -0.07f, 0.34f));
        return new Result(out, mouth, tips);
    }

    /** @return matriz no joelho (para colar sangue na canela) */
    private static Matrix4f leg(EnumMap<Part, Matrix4f> out, Part upper, Part lower, Part knee, Part paw, Part claw,
                                float x, float h, float z, float l1, float l2, boolean front, double phase, float amp) {
        double s = Math.sin(phase);
        double lift = Math.max(0, -Math.cos(phase)); // joelho dobra quando a perna vai para frente
        float a1;
        float a2;
        if (front) {
            a1 = (float) (0.12 + amp * 0.45 * s);
            a2 = (float) (-0.18 + amp * 0.45 * lift);
        } else {
            a1 = (float) (-0.35 + amp * 0.35 * s);
            a2 = (float) (0.70 + amp * 0.25 * lift);
        }
        Matrix4f hip = new Matrix4f().translate(x, h, z).rotateX(a1);
        out.put(upper, segDown(new Matrix4f(hip), W_LEG, l1));
        Matrix4f kneeM = new Matrix4f(hip).translate(0, -l1, 0).rotateX(a2);
        out.put(knee, box(new Matrix4f(kneeM), -0.085f, -0.085f, -0.085f, 0.17f, 0.17f, 0.17f));
        out.put(lower, segDown(new Matrix4f(kneeM), W_LEG * 0.85f, l2));
        // Pata sempre deitada no chão, apontando para frente
        Matrix4f ankle = new Matrix4f(kneeM).translate(0, -l2, 0).rotateX(-(a1 + a2));
        out.put(paw, box(new Matrix4f(ankle), -0.075f, -0.03f, -0.06f, 0.15f, 0.07f, 0.27f));
        out.put(claw, box(new Matrix4f(ankle), -0.06f, -0.035f, 0.20f, 0.12f, 0.045f, 0.10f));
        return kneeM;
    }

    /** Segmento que sai do ponto atual para baixo. */
    private static Matrix4f segDown(Matrix4f m, float w, float len) {
        return m.translate(-w / 2, -len, -w / 2).scale(w, len, w);
    }

    /** Segmento que sai do ponto atual para cima. */
    private static Matrix4f segUp(Matrix4f m, float w, float len) {
        return new Matrix4f(m).translate(-w / 2, 0, -w / 2).scale(w, len, w);
    }

    private static Matrix4f box(Matrix4f m, float x, float y, float z, float sx, float sy, float sz) {
        return m.translate(x, y, z).scale(sx, sy, sz);
    }
}
