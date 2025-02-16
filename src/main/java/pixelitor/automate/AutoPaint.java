/*
 * Copyright 2023 Laszlo Balazs-Csiki and Contributors
 *
 * This file is part of Pixelitor. Pixelitor is free software: you
 * can redistribute it and/or modify it under the terms of the GNU
 * General Public License, version 3 as published by the Free
 * Software Foundation.
 *
 * Pixelitor is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Pixelitor. If not, see <http://www.gnu.org/licenses/>.
 */

package pixelitor.automate;

import pixelitor.Composition;
import pixelitor.colors.Colors;
import pixelitor.filters.gui.DialogMenuBar;
import pixelitor.gui.utils.DialogBuilder;
import pixelitor.history.History;
import pixelitor.history.ImageEdit;
import pixelitor.layers.Drawable;
import pixelitor.tools.AbstractBrushTool;
import pixelitor.tools.Tool;
import pixelitor.tools.util.PPoint;
import pixelitor.utils.Lazy;
import pixelitor.utils.Messages;
import pixelitor.utils.ProgressHandler;
import pixelitor.utils.Rnd;

import java.awt.Color;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.util.SplittableRandom;

import static pixelitor.colors.FgBgColors.*;
import static pixelitor.tools.Tools.*;
import static pixelitor.utils.Texts.i18n;
import static pixelitor.utils.Threads.calledOnEDT;
import static pixelitor.utils.Threads.threadInfo;

/**
 * The "Auto Paint" functionality
 */
public class AutoPaint {
    public static final Tool[] ALLOWED_TOOLS = {SMUDGE, BRUSH, CLONE, ERASER};
    private static Color origFg;
    private static Color origBg;
    private static final Lazy<AutoPaintPanel> panelFactory = Lazy.of(AutoPaintPanel::new);

    private AutoPaint() {
    }

    public static void showDialog(Drawable dr) {
        var configPanel = panelFactory.get();
        new DialogBuilder()
            .validatedContent(configPanel)
            .title(i18n("auto_paint"))
            .menuBar(new DialogMenuBar(configPanel))
            .okAction(() -> autoPaint(dr, configPanel.getSettings()))
            .show();
    }

    private static void autoPaint(Drawable dr, AutoPaintSettings settings) {
        assert calledOnEDT() : threadInfo();

        BufferedImage backupImage = dr.getSelectedSubImage(true);
        String msg = "Auto Paint with " + settings.getTool().getName();
        ProgressHandler progressHandler = Messages.startProgress(msg, settings.getNumStrokes());

        try {
            saveFgBgColors();
            History.setIgnoreEdits(true);

            doAllStrokes(settings, dr, progressHandler);
        } catch (Exception e) {
            Messages.showException(e);
        } finally {
            History.setIgnoreEdits(false);
            History.add(new ImageEdit("Auto Paint", dr.getComp(),
                dr, backupImage, false));

            progressHandler.stopProgress();
            Messages.showPlainInStatusBar(msg + "finished.");

            restoreFgBgColors(settings);
        }
    }

    private static void doAllStrokes(AutoPaintSettings settings,
                                     Drawable dr,
                                     ProgressHandler progressHandler) {
        assert calledOnEDT() : threadInfo();

        var random = new SplittableRandom();
        var comp = dr.getComp();
        int numStrokes = settings.getNumStrokes();

        for (int i = 0; i < numStrokes; i++) {
            progressHandler.updateProgress(i);

            doSingleStroke(dr, settings, comp, random);
            comp.getView().paintImmediately();
        }
    }

    private static void doSingleStroke(Drawable dr,
                                       AutoPaintSettings settings,
                                       Composition comp,
                                       SplittableRandom rand) {
        assert calledOnEDT() : threadInfo();

        setFgBgColors(settings, rand);
        PPoint start = comp.getRandomPointInCanvas();
        PPoint end = settings.calcRandomEndPoint(start, comp, rand);

        Tool tool = settings.getTool();
        if (tool instanceof AbstractBrushTool abt) {
            Path2D shape = calcStrokePath(start, end, settings);
            abt.trace(dr, shape);
        } else {
            throw new IllegalStateException("tool = " + tool.getClass().getName());
        }
    }

    private static void setFgBgColors(AutoPaintSettings settings, SplittableRandom rand) {
        if (settings.useRandomColors()) {
            randomizeColors();
        } else if (settings.useInterpolatedColors()) {
            setFGColor(Colors.rgbInterpolate(origFg, origBg, rand.nextDouble()));
        }
    }

    private static Path2D calcStrokePath(PPoint start, PPoint end, AutoPaintSettings settings) {
        Path2D path = new Path2D.Double();
        path.moveTo(start.getImX(), start.getImY());
        double controlPointX = (start.getImX() + end.getImX()) / 2.0;
        double controlPointY = (start.getImY() + end.getImY()) / 2.0;
        double maxCurvature = settings.getMaxCurvature();
        if (maxCurvature > 0) {
            double maxShift = start.imDist(end) * maxCurvature;
            controlPointX += (Rnd.nextDouble() - 0.5) * maxShift;
            controlPointY += (Rnd.nextDouble() - 0.5) * maxShift;
        }
        path.quadTo(controlPointX, controlPointY, end.getImX(), end.getImY());
        return path;
    }

    private static void saveFgBgColors() {
        origFg = getFGColor();
        origBg = getBGColor();
    }

    private static void restoreFgBgColors(AutoPaintSettings settings) {
        // if colors were changed, restore the original
        if (settings.changeColors()) {
            setFGColor(origFg);
            setBGColor(origBg);
        }
    }
}
