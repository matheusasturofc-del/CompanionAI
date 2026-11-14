package dev.companionai.monster;

import org.joml.Matrix4f;

import java.util.EnumMap;

/**
 * Esqueleto do monstro (só matemática, sem Bukkit).
 * Coordenadas locais em blocos: +Y para cima, +Z para frente, origem no chão.
 * Cada parte é um bloco 1x1x1 esticado/girado por uma matriz.
 */
public final class MonsterPose {

    public enum Part {
        SPINE, CHEST, PELVIS,
        RIB_L1, RIB_R1, RIB_L2, RIB_R2, RIB_L3, RIB_R3,
        LEG_FL_U, LEG_FL_L, LEG_FR_U, LEG_FR_L,
        LEG_BL_U, LEG_BL_L, LEG_BR_U, LEG_BR_L,
        TAIL,
        NECK(true, false), HEAD(true, false), JAW(true, true), EAR_L(true, false), EAR_R(true, false);

        /** Escondido para quem controla (senão tapa a câmera). */
        public final boolean head;
        /** Bloco vermelho (sangue) em vez de branco. */
        public final boolean blood;

        Part() { this(false, false); }

        Part(boolean head, boolean blood) {
            this.head = head;
            this.blood = blood;
        }
    }

    /** Altura da coluna. As pernas são finas e compridas. */
    static final float H = 1.97f;
    static final float W_LEG = 0.12f;

    private MonsterPose() {}

    /**
     * @param phase    fase da caminhada (radianos)
     * @param amp      intensidade da passada (0 = parado)
     * @param headPitch inclinação da cabeça (radianos, + = para baixo)
     * @param headYaw  giro da cabeça (tique nervoso)
     * @param headRoll cabeça torta (tique nervoso)
     * @param jawOpen  abertura da boca (radianos)
     * @param tailSway balanço do rabo
     */
    public static EnumMap<Part, Matrix4f> compute(double phase, double amp, double headPitch, double headYaw,
                                                  double headRoll, double jawOpen, double tailSway) {
        EnumMap<Part, Matrix4f> out = new EnumMap<>(Part.class);
        float a = (float) amp;
        float bob = (float) (-0.05 * amp * Math.abs(Math.sin(phase)));
        float h = H + bob;

        // Corpo
        out.put(Part.SPINE, box(new Matrix4f(), -0.13f, h - 0.05f, -1.95f, 0.26f, 0.20f, 1.70f));
        out.put(Part.CHEST, box(new Matrix4f(), -0.17f, h - 0.30f, -0.88f, 0.34f, 0.42f, 0.60f));
        out.put(Part.PELVIS, box(new Matrix4f(), -0.16f, h - 0.12f, -1.92f, 0.32f, 0.28f, 0.36f));

        // Costelas penduradas na barriga
        float[] ribZ = {-1.00f, -1.20f, -1.40f};
        Part[][] ribs = {{Part.RIB_L1, Part.RIB_R1}, {Part.RIB_L2, Part.RIB_R2}, {Part.RIB_L3, Part.RIB_R3}};
        for (int i = 0; i < 3; i++) {
            for (int side = 0; side < 2; side++) {
                float sx = side == 0 ? 1f : -1f;
                Matrix4f m = new Matrix4f().translate(sx * 0.11f, h, ribZ[i]).rotateZ(sx * 0.38f);
                out.put(ribs[i][side], segDown(m, 0.05f, 0.42f - i * 0.05f));
            }
        }

        // Pernas (trote: diagonais juntas)
        double p0 = phase;
        double p1 = phase + Math.PI;
        leg(out, Part.LEG_FL_U, Part.LEG_FL_L, 0.19f, h, -0.50f, 1.00f, 1.00f, true, p0, a);
        leg(out, Part.LEG_FR_U, Part.LEG_FR_L, -0.19f, h, -0.50f, 1.00f, 1.00f, true, p1, a);
        leg(out, Part.LEG_BL_U, Part.LEG_BL_L, 0.19f, h, -1.75f, 1.05f, 1.05f, false, p1, a);
        leg(out, Part.LEG_BR_U, Part.LEG_BR_L, -0.19f, h, -1.75f, 1.05f, 1.05f, false, p0, a);

        // Rabo
        Matrix4f tail = new Matrix4f().translate(0, h + 0.05f, -1.92f)
                .rotateY((float) tailSway).rotateX(0.6f + (float) (0.1 * Math.sin(phase * 0.5)));
        out.put(Part.TAIL, segDown(tail, 0.07f, 0.75f));

        // Pescoço comprido para cima e para frente
        float neckLen = 0.95f;
        float neckAngle = 0.55f;
        Matrix4f neck = new Matrix4f().translate(0, h + 0.08f, -0.40f).rotateX(neckAngle);
        out.put(Part.NECK, segUp(neck, 0.14f, neckLen));

        // Cabeça na ponta do pescoço
        Matrix4f head = new Matrix4f(neck).translate(0, neckLen, 0).rotateX(-neckAngle)
                .rotateY((float) headYaw).rotateX((float) headPitch).rotateZ((float) headRoll);
        out.put(Part.HEAD, box(new Matrix4f(head), -0.15f, -0.14f, -0.14f, 0.30f, 0.28f, 0.64f));

        // Mandíbula vermelha (boca aberta com sangue)
        Matrix4f jaw = new Matrix4f(head).translate(0, -0.14f, 0.0f).rotateX((float) jawOpen);
        out.put(Part.JAW, box(jaw, -0.12f, -0.09f, 0.0f, 0.24f, 0.09f, 0.48f));

        // Orelhas compridas para trás
        for (int side = 0; side < 2; side++) {
            float sx = side == 0 ? 1f : -1f;
            Matrix4f ear = new Matrix4f(head).translate(sx * 0.09f, 0.12f, -0.06f)
                    .rotateZ(-sx * 0.18f).rotateX(-1.0f);
            out.put(side == 0 ? Part.EAR_L : Part.EAR_R, segUp(ear, 0.06f, 0.5f));
        }
        return out;
    }

    private static void leg(EnumMap<Part, Matrix4f> out, Part upper, Part lower, float x, float h, float z,
                            float l1, float l2, boolean front, double phase, float amp) {
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
        Matrix4f knee = new Matrix4f(hip).translate(0, -l1, 0).rotateX(a2);
        out.put(lower, segDown(knee, W_LEG * 0.85f, l2));
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
