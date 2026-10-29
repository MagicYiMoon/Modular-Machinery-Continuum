/* SPDX-License-Identifier: GPL-3.0-only
 * MMC (Modular Machinery: Continuum), modified 2026-10. Opt-in offscreen regression; not packaged in the mod.
 */
package hellfirepvp.modularmachinery.client.preview;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.opengl.*;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;

/** Actual GL pixel/depth/state assertions, without opening a visible window or starting Minecraft. */
public final class WorldProjectionRenderStateChecks {
    private static final int SIZE = 16;
    private static int assertions;

    public static void main(String[] args) {
        checkWorldView();
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        RenderSystem.initRenderThread();
        GLFWErrorCallback error = GLFWErrorCallback.createPrint(System.err);
        error.set();
        long window = 0;
        try {
            require(GLFW.glfwInit(), "GLFW could not initialize");
            GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
            GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, 3);
            GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, 2);
            GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE, GLFW.GLFW_OPENGL_CORE_PROFILE);
            window = GLFW.glfwCreateWindow(SIZE, SIZE, "MMCE offscreen projection check", 0, 0);
            require(window != 0, "hidden GL context could not be created");
            GLFW.glfwMakeContextCurrent(window);
            GL.createCapabilities();
            System.out.println("GL renderer: " + glGetString(GL_RENDERER) + "; version: " + glGetString(GL_VERSION));
            BlueprintFluidRenderChecks.run();
            run();
            System.out.println("[MMCE][projection-gl] PASS: " + assertions + " assertions; blend pixels, occlusion/depth, "
                    + "target/texture/format preservation, batch routing/cache, setup/draw/cleanup failures and state restoration");
        } finally {
            if (window != 0) GLFW.glfwDestroyWindow(window);
            GLFW.glfwTerminate();
            error.free();
        }
    }

    /** Catch the AFTER_LEVEL regression independently of GL blend/texture assertions. */
    private static void checkWorldView() {
        var frame = new ProjectionViewFrame();
        require(frame.take() == null, "frame must not invent a world view");
        for (float yaw : new float[]{0, 90, 180, 270}) {
            for (float pitch : new float[]{-55, 0, 70}) {
                PoseStack world = new PoseStack();
                world.mulPose(com.mojang.math.Axis.XP.rotationDegrees(pitch));
                world.mulPose(com.mojang.math.Axis.YP.rotationDegrees(yaw + 180));
                org.joml.Matrix4f expected = new org.joml.Matrix4f(world.last().pose());
                org.joml.Matrix3f normals = new org.joml.Matrix3f(world.last().normal());
                frame.capture(world);
                world.setIdentity(); // LevelRenderer and later callbacks may mutate the original stack.
                PoseStack captured = frame.take();
                require(captured.last().pose().equals(expected, 0.00001F)
                                && captured.last().normal().equals(normals, 0.00001F), "world camera rotation was lost");
                captured.translate(-2055, -66.62, -2029);
                var block = new org.joml.Vector3f(2057, 65, 2033);
                var actual = captured.last().pose().transformPosition(new org.joml.Vector3f(block));
                var oracle = expected.transformPosition(block.sub(2055, 66.62F, 2029));
                require(actual.distance(oracle) < .001F, "camera position applied twice or world anchor drifted");
                require(frame.take() == null, "stale view leaked into another frame");
            }
        }
        frame.capture(new PoseStack()); frame.clear();
        require(frame.take() == null, "resource/world reload retained stale view");
    }

    private static void run() {
        int target = glGenFramebuffers(), otherTarget = glGenFramebuffers();
        int color = glGenTextures(), depth = glGenRenderbuffers();
        int vao = glGenVertexArrays(), vbo = glGenBuffers(), program = program();
        glBindFramebuffer(GL_FRAMEBUFFER, target);
        glBindTexture(GL_TEXTURE_2D, color);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, SIZE, SIZE, 0, GL_RGBA, GL_UNSIGNED_BYTE, (ByteBuffer) null);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, color, 0);
        glBindRenderbuffer(GL_RENDERBUFFER, depth);
        glRenderbufferStorage(GL_RENDERBUFFER, GL_DEPTH_COMPONENT24, SIZE, SIZE);
        glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_RENDERBUFFER, depth);
        require(glCheckFramebufferStatus(GL_FRAMEBUFFER) == GL_FRAMEBUFFER_COMPLETE, "offscreen target is incomplete");
        glViewport(0, 0, SIZE, SIZE);
        glBindVertexArray(vao);
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferData(GL_ARRAY_BUFFER, new float[]{-1, -1, 3, -1, -1, 3}, GL_STATIC_DRAW);
        int attribute = glGetAttribLocation(program, "position");
        glEnableVertexAttribArray(attribute);
        glVertexAttribPointer(attribute, 2, GL_FLOAT, false, 8, 0L);
        glUseProgram(program);
        glUniform4f(glGetUniformLocation(program, "sourceColor"), .8F, .3F, .1F, 1);
        glUniform1f(glGetUniformLocation(program, "z"), .5F); // Depth .75, behind the world's depth .2.
        List<String> calls = new ArrayList<>();
        Runnable bindTarget = () -> { calls.add("target"); glBindFramebuffer(GL_FRAMEBUFFER, target); };
        var types = new WorldProjectionRenderTypes(bindTarget);
        var source = new RenderType("test_solid", DefaultVertexFormat.BLOCK, VertexFormat.Mode.QUADS, 256, true, false,
                () -> {
                    calls.add("source-setup");
                    glBindFramebuffer(GL_FRAMEBUFFER, otherTarget);
                    glBindTexture(GL_TEXTURE_2D, color);
                    RenderSystem.enableDepthTest(); RenderSystem.depthMask(true); RenderSystem.disableBlend();
                }, () -> { calls.add("source-clear"); glBindFramebuffer(GL_FRAMEBUFFER, otherTarget); }) { };
        var ghost = types.type(source);
        try {
            WorldProjectionRenderTypes.restoreWorldState();
            clear(target);
            // Negative control: ordinary depth-tested geometry at this depth must remain hidden.
            source.setupRenderState(); bindTarget.run(); glDrawArrays(GL_TRIANGLES, 0, 3);
            checkPixel(new float[]{.2F, .4F, .6F}, "ordinary geometry was not hidden behind world depth");
            calls.clear();
            ghost.setupRenderState();
            require(calls.equals(List.of("source-setup", "target")), "ghost state applied before source/target setup");
            require(glGetInteger(GL_FRAMEBUFFER_BINDING) == target && glGetInteger(GL_TEXTURE_BINDING_2D) == color,
                    "original output target overrode ghost target, or original texture was discarded");
            require(ghost.format() == source.format() && ghost.mode() == source.mode()
                            && ghost.bufferSize() == source.bufferSize()
                            && ghost.affectsCrumbling() == source.affectsCrumbling(),
                    "ghost lost original vertex/crumbling contract");
            require(!glIsEnabled(GL_DEPTH_TEST) && !glGetBoolean(GL_DEPTH_WRITEMASK) && glIsEnabled(GL_BLEND),
                    "ghost depth/blend state incorrect");
            require(glGetInteger(GL14.GL_BLEND_SRC_RGB) == GL_ONE_MINUS_DST_COLOR
                            && glGetInteger(GL14.GL_BLEND_DST_RGB) == GL_DST_COLOR, "ghost blend factors differ from source");
            float beforeDepth = depth();
            glDrawArrays(GL_TRIANGLES, 0, 3);
            // Independent arithmetic oracle: output = source*(1-destination) + destination*destination.
            checkPixel(new float[]{.68F, .34F, .4F}, "source blend equation/through-world ghost pixels incorrect");
            require(Math.abs(depth() - beforeDepth) < .00001F, "ghost changed the world's depth buffer");
            ghost.clearRenderState();
            require(calls.equals(List.of("source-setup", "target", "source-clear", "target")), "source cleanup/target restoration missing");
            checkRestored(target);
            // Repeat from a different background to catch a fixed tint or accidental standard alpha blending.
            glClearColor(.8F, .1F, .5F, 1); glClear(GL_COLOR_BUFFER_BIT);
            ghost.setupRenderState(); glDrawArrays(GL_TRIANGLES, 0, 3);
            checkPixel(new float[]{.8F, .28F, .3F}, "destination-color blend did not respond to background");
            ghost.clearRenderState();

            require(types.type(source) == ghost, "identical render type lost cache identity");
            var entitySource = new RenderType("test_ber", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS,
                    256, false, false, () -> {}, () -> {}) {};
            require(types.type(entitySource) != ghost && types.type(entitySource).format() == DefaultVertexFormat.NEW_ENTITY,
                    "BER texture/format batches collapsed into block geometry");
            RenderType[] routed = new RenderType[1];
            var builder = new BufferBuilder(256);
            MultiBufferSource wrapped = types.wrap(type -> { routed[0] = type; return builder; });
            require(wrapped.getBuffer(source) == builder && routed[0] == ghost, "model/BER output did not reach ghost batch");
            types.clear(); require(types.type(source) != ghost, "scene/resource reset retained wrapped render types");
            checkFailures(types, target);
            require(glGetError() == GL_NO_ERROR, "OpenGL error after projection checks");
        } finally {
            WorldProjectionRenderTypes.restoreWorldState();
            glUseProgram(0); glBindFramebuffer(GL_FRAMEBUFFER, 0);
            glDeleteProgram(program); glDeleteBuffers(vbo); glDeleteVertexArrays(vao);
            glDeleteRenderbuffers(depth); glDeleteTextures(color);
            glDeleteFramebuffers(target); glDeleteFramebuffers(otherTarget);
        }
    }

    private static void checkFailures(WorldProjectionRenderTypes types, int target) {
        int[] cleared = {0};
        var setupFailure = new RenderType("failing-setup", DefaultVertexFormat.POSITION, VertexFormat.Mode.QUADS,
                256, false, false, () -> { RenderSystem.depthMask(false); throw new IllegalStateException("expected setup failure"); },
                () -> cleared[0]++) {};
        var builder = new BufferBuilder(256);
        quad(builder);
        try { types.type(setupFailure).end(builder, VertexSorting.DISTANCE_TO_ORIGIN); throw new AssertionError("setup failure was swallowed"); }
        catch (IllegalStateException expected) { require(expected.getMessage().equals("expected setup failure"), "unexpected setup failure"); }
        require(cleared[0] == 1 && !builder.building(), "failed batch did not end or clear source state");
        checkRestored(target);
        // A failed setup must release the ended buffer so the builder can safely be used again.
        quad(builder); builder.end().release();
        var cleanupFailure = new RenderType("failing-cleanup", DefaultVertexFormat.POSITION, VertexFormat.Mode.QUADS,
                256, false, false, () -> {}, () -> { throw new IllegalStateException("expected cleanup failure"); }) {};
        var ghost = types.type(cleanupFailure); ghost.setupRenderState();
        try { ghost.clearRenderState(); throw new AssertionError("cleanup failure was swallowed"); }
        catch (IllegalStateException expected) { require(expected.getMessage().equals("expected cleanup failure"), "unexpected cleanup failure"); }
        checkRestored(target);
    }

    private static void quad(BufferBuilder builder) {
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
        for (float[] vertex : new float[][]{{0, 0, 0}, {1, 0, 0}, {1, 1, 0}, {0, 1, 0}}) {
            builder.vertex(vertex[0], vertex[1], vertex[2]).endVertex();
        }
    }
    private static void checkRestored(int target) {
        require(glIsEnabled(GL_DEPTH_TEST) && glGetBoolean(GL_DEPTH_WRITEMASK) && !glIsEnabled(GL_BLEND), "depth/blend state leaked after ghost");
        require(glGetInteger(GL14.GL_BLEND_SRC_RGB) == GL_SRC_ALPHA && glGetInteger(GL14.GL_BLEND_DST_RGB) == GL_ONE_MINUS_SRC_ALPHA,
                "default blend function not restored");
        require(glGetInteger(GL_FRAMEBUFFER_BINDING) == target, "main target not restored");
    }
    private static void clear(int target) {
        glBindFramebuffer(GL_FRAMEBUFFER, target); glDepthFunc(GL_LESS);
        glClearColor(.2F, .4F, .6F, 1); glClearDepth(.2); glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
    }
    private static float depth() {
        var pixel = BufferUtils.createFloatBuffer(1);
        glReadPixels(SIZE / 2, SIZE / 2, 1, 1, GL_DEPTH_COMPONENT, GL_FLOAT, pixel);
        return pixel.get(0);
    }
    private static void checkPixel(float[] expected, String message) {
        var pixel = BufferUtils.createByteBuffer(4);
        glReadPixels(SIZE / 2, SIZE / 2, 1, 1, GL_RGBA, GL_UNSIGNED_BYTE, pixel);
        for (int i = 0; i < 3; i++) require(Math.abs((pixel.get(i) & 255) / 255F - expected[i]) < .012F,
                message + "; channel " + i + ": got " + (pixel.get(i) & 255) + ", expected " + expected[i] * 255);
    }
    private static int program() {
        int vertex = shader(GL_VERTEX_SHADER, "#version 150\nin vec2 position; uniform float z; void main(){gl_Position=vec4(position,z,1.0);}");
        int fragment = shader(GL_FRAGMENT_SHADER, "#version 150\nuniform vec4 sourceColor; out vec4 color; void main(){color=sourceColor;}");
        int program = glCreateProgram(); glAttachShader(program, vertex); glAttachShader(program, fragment); glLinkProgram(program);
        require(glGetProgrami(program, GL_LINK_STATUS) != 0, "GL shader link: " + glGetProgramInfoLog(program));
        glDeleteShader(vertex); glDeleteShader(fragment); return program;
    }
    private static int shader(int kind, String source) {
        int shader = glCreateShader(kind); glShaderSource(shader, source); glCompileShader(shader);
        require(glGetShaderi(shader, GL_COMPILE_STATUS) != 0, "GL shader compile: " + glGetShaderInfoLog(shader));
        return shader;
    }
    private static void require(boolean condition, String message) { assertions++; if (!condition) throw new AssertionError(message); }
}
