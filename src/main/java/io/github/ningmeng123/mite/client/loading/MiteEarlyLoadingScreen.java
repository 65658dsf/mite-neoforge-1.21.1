package io.github.ningmeng123.mite.client.loading;

import static org.lwjgl.opengl.GL32C.*;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import net.neoforged.fml.earlydisplay.DisplayWindow;
import net.neoforged.fml.earlydisplay.EarlyFramebuffer;
import net.neoforged.fml.earlydisplay.ElementShader;
import net.neoforged.fml.earlydisplay.QuadHelper;
import net.neoforged.fml.earlydisplay.RenderElement;
import net.neoforged.fml.earlydisplay.SimpleBufferBuilder;
import net.neoforged.fml.earlydisplay.SimpleFont;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.fml.loading.ImmediateWindowHandler;
import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.stb.STBImage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Adds MITE artwork to NeoForge's existing early window. @author NingMeng123 */
public final class MiteEarlyLoadingScreen {
    private static final Logger LOGGER = LoggerFactory.getLogger(MiteEarlyLoadingScreen.class);
    private static final int BACKGROUND_UNIT = 9;
    private static final String AUTHOR = "Author NingMeng123";
    private static volatile MiteEarlyLoadingScreen active;

    private final DisplayWindow display;
    private final EarlyFramebuffer framebuffer;
    private final Field displayContextField;
    private final Field framebufferContextField;
    private final int framebufferTexture;
    private final SimpleFont font;
    private final String version;
    private final String backgroundPath;
    private final long window;
    private final int lineSpacing;
    private final int[] framebufferWidth = new int[1];
    private final int[] framebufferHeight = new int[1];
    private final int[] previousScissor = new int[4];
    private Texture background;
    private boolean disabled;

    private MiteEarlyLoadingScreen(DisplayWindow display, String version) throws ReflectiveOperationException {
        this.display = display;
        this.framebuffer = (EarlyFramebuffer) field(DisplayWindow.class, "framebuffer").get(display);
        this.displayContextField = field(DisplayWindow.class, "context");
        this.framebufferContextField = field(EarlyFramebuffer.class, "context");
        this.framebufferTexture = field(EarlyFramebuffer.class, "texture").getInt(framebuffer);
        this.font = (SimpleFont) field(DisplayWindow.class, "font").get(display);
        this.window = field(DisplayWindow.class, "window").getLong(display);
        this.lineSpacing = field(SimpleFont.class, "lineSpacing").getInt(font);
        this.version = "Mite: Modern " + version;
        this.backgroundPath = "/assets/mite/textures/gui/loading/background_"
                + ThreadLocalRandom.current().nextInt(1, 3) + ".png";
    }

    public static void install() {
        try {
            Object provider = field(ImmediateWindowHandler.class, "provider").get(null);
            if (!(provider instanceof DisplayWindow display)) {
                return;
            }
            String version = FMLLoader.getLoadingModList().getMods().stream()
                    .filter(mod -> mod.getModId().equals("mite"))
                    .findFirst().orElseThrow().getVersion().toString();
            Future<?> initialization = (Future<?>) field(DisplayWindow.class, "initializationFuture").get(display);
            initialization.get(10, TimeUnit.SECONDS);
            Semaphore renderLock = (Semaphore) field(DisplayWindow.class, "renderLock").get(display);
            if (!renderLock.tryAcquire(10, TimeUnit.SECONDS)) {
                LOGGER.warn("MITE loading artwork could not acquire the early-window renderer");
                return;
            }
            // Register callbacks before Minecraft takes over. GPU work starts on the render thread.
            try {
                MiteEarlyLoadingScreen screen = new MiteEarlyLoadingScreen(display, version);
                screen.attach(display);
                active = screen;
                LOGGER.info("MITE loading artwork installed using {}", screen.backgroundPath);
            } finally {
                renderLock.release();
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            LOGGER.warn("MITE loading artwork installation was interrupted", exception);
        } catch (Exception | LinkageError exception) {
            LOGGER.warn("Unable to initialize MITE loading artwork", exception);
        }
    }

    @SuppressWarnings("unchecked")
    private void attach(DisplayWindow display) throws ReflectiveOperationException {
        Field elementsField = field(DisplayWindow.class, "elements");
        List<RenderElement> original = (List<RenderElement>) elementsField.get(display);
        List<RenderElement> elements = new ArrayList<>(original) {
            @Override
            public void add(int index, RenderElement element) {
                // NeoForge inserts its Mojang logo at zero after handing the window to Minecraft.
                super.add(index == 0 ? 1 : index, element);
            }
        };
        // NeoForge has five standard elements, optionally preceded by its April Fools squirrel.
        int foxIndex = original.size() - 5;
        if (foxIndex >= 0) {
            RenderElement fox = elements.get(foxIndex);
            elements.set(foxIndex, moveUp(fox, 2));
            RenderElement logs = elements.get(foxIndex + 1);
            elements.set(foxIndex + 1, element((buffer, context, frame) -> renderLogs(logs, context, frame)));
        }
        // Populate a normal list first so the insertion rule only affects later NeoForge additions.
        List<RenderElement> ordered = new ArrayList<>();
        ordered.add(element(this::renderBackground));
        ordered.addAll(elements);
        ordered.add(moveUp(RenderElement.forgeVersionOverlay(font, version), 2));
        ordered.add(moveUp(RenderElement.forgeVersionOverlay(font, AUTHOR), 1));
        elements.clear();
        elements.addAll(ordered);
        elementsField.set(display, elements);
    }

    private void renderBackground(SimpleBufferBuilder buffer, RenderElement.DisplayContext context, int frame) {
        if (disabled) {
            return;
        }
        try {
            if (background == null) {
                background = loadTexture(backgroundPath, BACKGROUND_UNIT);
            }
            GLFW.glfwGetFramebufferSize(window, framebufferWidth, framebufferHeight);
            context = resizeFramebuffer(context);
            float screenAspect = framebufferHeight[0] > 0
                    ? (float) framebufferWidth[0] / framebufferHeight[0]
                    : (float) context.width() / context.height();
            float imageAspect = (float) background.width() / background.height();
            float visibleWidth = Math.min(1f, screenAspect / imageAspect);
            float visibleHeight = Math.min(1f, imageAspect / screenAspect);
            drawTexture(buffer, context, background, BACKGROUND_UNIT, 0, 0,
                    context.scaledWidth(), context.scaledHeight(),
                    (1f - visibleWidth) / 2f, (1f + visibleWidth) / 2f,
                    (1f - visibleHeight) / 2f, (1f + visibleHeight) / 2f);
            context.elementShader().updateRenderTypeUniform(ElementShader.RenderType.BAR);
            buffer.begin(SimpleBufferBuilder.Format.POS_TEX_COLOR, SimpleBufferBuilder.Mode.QUADS);
            QuadHelper.loadQuad(buffer, 0, context.scaledWidth(), 0, context.scaledHeight(),
                    0, 1, 0, 1, 0x55000000);
            buffer.draw();
        } catch (IOException | ReflectiveOperationException | RuntimeException exception) {
            disabled = true;
            deleteTextures();
            LOGGER.warn("Unable to render MITE loading artwork", exception);
        }
    }

    private RenderElement.DisplayContext resizeFramebuffer(RenderElement.DisplayContext context)
            throws ReflectiveOperationException {
        if (framebufferWidth[0] <= 0 || framebufferHeight[0] <= 0) return context;
        float aspect = (float) framebufferWidth[0] / framebufferHeight[0];
        int width = Math.max(854, Math.round(480 * aspect));
        int height = Math.max(480, Math.round(854 / aspect));
        if (width == context.width() && height == context.height()) return context;

        var resized = new RenderElement.DisplayContext(width, height, context.scale(),
                context.elementShader(), context.colourScheme(), context.performance());
        int previousTexture = glGetInteger(GL_TEXTURE_BINDING_2D);
        try {
            glBindTexture(GL_TEXTURE_2D, framebufferTexture);
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, resized.scaledWidth(), resized.scaledHeight(),
                    0, GL_RGBA, GL_UNSIGNED_BYTE, (ByteBuffer) null);
        } finally {
            glBindTexture(GL_TEXTURE_2D, previousTexture);
        }
        // Both render paths use this context when fitting the framebuffer into the actual window.
        displayContextField.set(display, resized);
        framebufferContextField.set(framebuffer, resized);
        glViewport(0, 0, resized.scaledWidth(), resized.scaledHeight());
        resized.elementShader().updateScreenSizeUniform(resized.scaledWidth(), resized.scaledHeight());
        return resized;
    }

    private RenderElement moveUp(RenderElement original, int lines) throws ReflectiveOperationException {
        return element((buffer, context, frame) -> original.render(new RenderElement.DisplayContext(
                context.width(), context.height() - (lines * lineSpacing + context.scale() - 1) / context.scale(),
                context.scale(), context.elementShader(), context.colourScheme(), context.performance()), frame));
    }

    private void renderLogs(RenderElement logs, RenderElement.DisplayContext context, int frame) {
        int footerWidth = Math.max(font.stringWidth(version), font.stringWidth(AUTHOR));
        boolean scissorEnabled = glIsEnabled(GL_SCISSOR_TEST);
        glGetIntegerv(GL_SCISSOR_BOX, previousScissor);
        glEnable(GL_SCISSOR_TEST);
        glScissor(0, 0, Math.max(0, context.scaledWidth() - footerWidth - 24), context.scaledHeight());
        try {
            logs.render(context, frame);
        } finally {
            glScissor(previousScissor[0], previousScissor[1], previousScissor[2], previousScissor[3]);
            if (!scissorEnabled) glDisable(GL_SCISSOR_TEST);
        }
    }

    private static void drawTexture(SimpleBufferBuilder buffer, RenderElement.DisplayContext context,
            Texture texture, int unit, float left, float top, float right, float bottom,
            float u0, float u1, float v0, float v1) {
        int previousActive = glGetInteger(GL_ACTIVE_TEXTURE);
        glActiveTexture(GL_TEXTURE0 + unit);
        int previousTexture = glGetInteger(GL_TEXTURE_BINDING_2D);
        glBindTexture(GL_TEXTURE_2D, texture.id());
        context.elementShader().updateTextureUniform(unit);
        context.elementShader().updateRenderTypeUniform(ElementShader.RenderType.TEXTURE);
        buffer.begin(SimpleBufferBuilder.Format.POS_TEX_COLOR, SimpleBufferBuilder.Mode.QUADS);
        QuadHelper.loadQuad(buffer, left, right, top, bottom, u0, u1, v0, v1, 0xFFFFFFFF);
        buffer.draw();
        glBindTexture(GL_TEXTURE_2D, previousTexture);
        glActiveTexture(previousActive);
    }

    private static Texture loadTexture(String path, int unit) throws IOException {
        byte[] bytes;
        try (var stream = MiteEarlyLoadingScreen.class.getResourceAsStream(path)) {
            if (stream == null) {
                throw new IOException("Missing loading texture " + path);
            }
            bytes = stream.readAllBytes();
        }
        ByteBuffer encoded = BufferUtils.createByteBuffer(bytes.length).put(bytes).flip();
        int[] width = new int[1];
        int[] height = new int[1];
        int[] channels = new int[1];
        ByteBuffer pixels = STBImage.stbi_load_from_memory(encoded, width, height, channels, 4);
        if (pixels == null) {
            throw new IOException("Unable to decode loading texture " + path + ": " + STBImage.stbi_failure_reason());
        }
        int previousActive = glGetInteger(GL_ACTIVE_TEXTURE);
        glActiveTexture(GL_TEXTURE0 + unit);
        int previousTexture = glGetInteger(GL_TEXTURE_BINDING_2D);
        int id = glGenTextures();
        try {
            glBindTexture(GL_TEXTURE_2D, id);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, width[0], height[0], 0, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
            return new Texture(id, width[0], height[0]);
        } catch (RuntimeException exception) {
            glDeleteTextures(id);
            throw exception;
        } finally {
            STBImage.stbi_image_free(pixels);
            glBindTexture(GL_TEXTURE_2D, previousTexture);
            glActiveTexture(previousActive);
        }
    }

    public static void releaseTextures() {
        MiteEarlyLoadingScreen screen = active;
        if (screen != null) {
            screen.deleteTextures();
            active = null;
        }
    }

    private void deleteTextures() {
        if (background != null) {
            glDeleteTextures(background.id());
            background = null;
        }
    }

    private static Field field(Class<?> owner, String name) throws ReflectiveOperationException {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static RenderElement element(ElementRenderer renderer) throws ReflectiveOperationException {
        // The early-display API exposes RenderElement but keeps its two callback interfaces package-private.
        ClassLoader loader = RenderElement.class.getClassLoader();
        Class<?> rendererType = Class.forName("net.neoforged.fml.earlydisplay.RenderElement$Renderer", false, loader);
        Class<?> initializerType = Class.forName("net.neoforged.fml.earlydisplay.RenderElement$Initializer", false, loader);
        Object callback = Proxy.newProxyInstance(loader, new Class<?>[] { rendererType }, (proxy, method, arguments) -> {
            renderer.render((SimpleBufferBuilder) arguments[0], (RenderElement.DisplayContext) arguments[1], (int) arguments[2]);
            return null;
        });
        Object initializer = Proxy.newProxyInstance(loader, new Class<?>[] { initializerType }, (proxy, method, arguments) -> callback);
        Constructor<RenderElement> constructor = RenderElement.class.getDeclaredConstructor(initializerType);
        constructor.setAccessible(true);
        return constructor.newInstance(initializer);
    }

    @FunctionalInterface
    private interface ElementRenderer {
        void render(SimpleBufferBuilder buffer, RenderElement.DisplayContext context, int frame);
    }

    private record Texture(int id, int width, int height) {}
}
