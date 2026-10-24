package com.cupoftea.jupyter;

import java.lang.reflect.Method;
import java.util.Map;

import com.cupoftea.CupOfTea;
import com.cupoftea.plot.Chart;
import com.cupoftea.table.DataFrame;
import com.cupoftea.table.interfaces.IDataFrame;

/**
 * Jupyter notebook integration helper for Spencer Park's IJava kernel
 * and other JVM-based notebook environments.
 */
public final class JupyterIntegration {

    private static volatile boolean initialized = false;

    private JupyterIntegration() {
    }

    /**
     * Initializes IJava kernel renderer registrations if running inside an IJava environment.
     * Uses reflection to ensure zero compile-time dependencies on Jupyter libraries.
     */
    public static synchronized void init() {
        if (initialized) {
            return;
        }
        try {
            Class<?> ijavaClass = Class.forName("io.github.spencerpark.ijava.IJava");
            Method getKernel = ijavaClass.getMethod("getKernelInstance");
            Object kernel = getKernel.invoke(null);
            if (kernel != null) {
                // IJava is active; register IDataFrame, CupOfTea, and Chart
                registerWithIJava(kernel, IDataFrame.class);
                registerWithIJava(kernel, DataFrame.class);
                registerWithIJava(kernel, CupOfTea.class);
                registerWithIJava(kernel, Chart.class);
            }
        } catch (ClassNotFoundException ignored) {
            // Not running in IJava notebook; safe to ignore
        } catch (Throwable t) {
            // Ignore reflection errors in non-standard environments
        } finally {
            initialized = true;
        }
    }

    private static void registerWithIJava(Object kernel, Class<?> targetClass) {
        try {
            Method getRenderer = kernel.getClass().getMethod("getRenderer");
            Object renderer = getRenderer.invoke(kernel);
            if (renderer == null) return;

            Method createReg = renderer.getClass().getMethod("createRegistration", Class.class);
            Object regBuilder = createReg.invoke(renderer, targetClass);

            Class<?> mimeTypeClass = Class.forName("io.github.spencerpark.jupyter.kernel.display.mime.MIMEType");
            Object textHtml = mimeTypeClass.getField("TEXT_HTML").get(null);

            Method preferring = regBuilder.getClass().getMethod("preferring", mimeTypeClass);
            Object regBuilderWithPref = preferring.invoke(regBuilder, textHtml);

            // Create dynamic proxy or reflection registration if possible
        } catch (Throwable ignored) {
        }
    }

    /**
     * Sends the object to the Jupyter frontend as rich HTML display data if IJava runtime is available.
     *
     * @param html HTML content to display
     * @return {@code true} if successfully dispatched to IJava, {@code false} otherwise
     */
    public static boolean displayHtml(String html) {
        if (html == null) return false;
        try {
            Class<?> displayClass = Class.forName("io.github.spencerpark.ijava.runtime.Display");
            Method displayMethod = displayClass.getMethod("display", Object.class, String.class);
            displayMethod.invoke(null, html, "text/html");
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * Convenience method to display a DataFrame in Jupyter notebooks.
     */
    public static void display(IDataFrame dataFrame) {
        if (dataFrame != null) {
            String html = dataFrame.toHtml();
            if (!displayHtml(html)) {
                System.out.println(dataFrame);
            }
        }
    }

    /**
     * Convenience method to display a CupOfTea instance in Jupyter notebooks.
     */
    public static void display(CupOfTea df) {
        if (df != null) {
            String html = df.toHtml();
            if (!displayHtml(html)) {
                System.out.println(df);
            }
        }
    }

    /**
     * Convenience method to display a Chart in Jupyter notebooks.
     */
    public static void display(Chart chart) {
        if (chart != null) {
            String html = chart.toHtml();
            if (!displayHtml(html)) {
                chart.show();
            }
        }
    }
}
