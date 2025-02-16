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

package pixelitor.io;

import pixelitor.Canvas;
import pixelitor.Composition;
import pixelitor.Views;
import pixelitor.automate.DirectoryChooser;
import pixelitor.filters.gui.StrokeParam;
import pixelitor.gui.GUIText;
import pixelitor.gui.utils.Dialogs;
import pixelitor.io.magick.ImageMagick;
import pixelitor.layers.Layer;
import pixelitor.utils.Messages;
import pixelitor.utils.Shapes;

import javax.imageio.ImageWriteParam;
import javax.swing.*;
import java.awt.EventQueue;
import java.awt.Shape;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;

import static java.lang.String.format;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.Files.isWritable;
import static pixelitor.io.FileChoosers.svgFilter;
import static pixelitor.utils.Threads.*;

/**
 * Utility class with static methods related to opening and saving files.
 */
public class IO {
    private IO() {
    }

    public static CompletableFuture<Composition> openFileAsync(File file,
                                                               boolean checkAlreadyOpen) {
        if (checkAlreadyOpen && !Views.warnIfAlreadyOpen(file)) {
            return CompletableFuture.completedFuture(null);
        }
        return loadCompAsync(file)
            .thenApplyAsync(Views::addJustLoadedComp, onEDT)
            .whenComplete((comp, e) -> handleReadingProblems(e));
    }

    public static CompletableFuture<Composition> loadCompAsync(File file) {
        // if the file format is not recognized, this will still try to
        // read it in a single-layered format, which doesn't have to be JPG
        FileFormat format = FileFormat.fromFile(file).orElse(FileFormat.JPG);
        return format.readAsync(file);
    }

    public static Composition loadCompSync(File file) {
        FileFormat format = FileFormat.fromFile(file).orElse(FileFormat.JPG);
        return format.readSync(file);
    }

    public static CompletableFuture<Void> loadNewImageLayerAsync(File file,
                                                                 Composition comp) {
        return CompletableFuture
            .supplyAsync(() -> TrackedIO.uncheckedRead(file), onIOThread)
            .thenAcceptAsync(img -> comp.addExternalImageAsNewLayer(
                    img, file.getName(), "Dropped Layer"),
                onEDT)
            .whenComplete((v, e) -> handleReadingProblems(e));
    }

    /**
     * Utility method designed to be used with CompletableFuture.
     * Can be called on any thread.
     */
    public static void handleReadingProblems(Throwable e) {
        if (e == null) {
            // do nothing if the stage didn't complete exceptionally
            return;
        }
        if (e instanceof CompletionException) {
            // if the exception was thrown in a previous
            // stage, handle it the same way
            handleReadingProblems(e.getCause());
            return;
        }
        if (e instanceof DecodingException de) {
            if (calledOnEDT()) {
                showDecodingError(de);
            } else {
                EventQueue.invokeLater(() -> showDecodingError(de));
            }
        } else {
            Messages.showExceptionOnEDT(e);
        }
    }

    private static void showDecodingError(DecodingException de) {
        String msg = de.getMessage();
        if (de.wasMagick()) {
            Messages.showError("Error", msg);
        } else {
            String[] options = {"Try with ImageMagick Import", GUIText.CANCEL};
            boolean doMagick = Dialogs.showOKCancelDialog(msg, "Error",
                options, 0, JOptionPane.ERROR_MESSAGE);
            if (doMagick) {
                ImageMagick.importComposition(de.getFile(), false);
            }
        }
    }

    /**
     * Returns true if the file was saved,
     * false if the user cancels the saving or if it could not be saved
     */
    public static boolean save(Composition comp, boolean saveAs) {
        boolean needsFileChooser = saveAs || comp.getFile() == null;
        if (needsFileChooser) {
            return FileChoosers.saveWithChooser(comp);
        } else {
            File file = comp.getFile();
            if (file.exists()) { // if it was not deleted in the meantime...
                if (!isWritable(file.toPath())) {
                    Dialogs.showFileNotWritableDialog(file);
                    return false;
                }
            }
            Optional<FileFormat> fileFormat = FileFormat.fromFile(file);
            if (fileFormat.isPresent()) {
                var saveSettings = new SaveSettings(fileFormat.get(), file);
                comp.saveAsync(saveSettings, true);
                return true;
            } else {
                // the file was read from a file with an unsupported
                // extension, save it with a file chooser
                return FileChoosers.saveWithChooser(comp);
            }
        }
    }

    public static void saveImageToFile(BufferedImage image,
                                       SaveSettings saveSettings) {
        FileFormat format = saveSettings.getFormat();
        File selectedFile = saveSettings.getFile();

        Objects.requireNonNull(format);
        Objects.requireNonNull(selectedFile);
        Objects.requireNonNull(image);
        assert calledOutsideEDT() : "on EDT";

        try {
            if (format == FileFormat.JPG) {
                JpegSettings settings = JpegSettings.from(saveSettings);
                Consumer<ImageWriteParam> customizer = settings.getJpegInfo().toCustomizer();
                TrackedIO.write(image, "jpg", selectedFile, customizer);
            } else {
                TrackedIO.write(image, format.toString(), selectedFile, null);
            }
        } catch (IOException e) {
            if (e.getMessage().contains("another process")) {
                // handle here, because we have the file information
                showAnotherProcessErrorMsg(selectedFile);
            } else {
                throw new UncheckedIOException(e);
            }
        }
    }

    private static void showAnotherProcessErrorMsg(File file) {
        String msg = format(
            "Can't save to%n%s%nbecause this file is being used by another program.",
            file.getAbsolutePath());

        EventQueue.invokeLater(() -> Messages.showError("Can't save", msg));
    }

    public static void openAllSupportedImagesInDir(File dir) {
        List<File> files = FileUtils.listSupportedInputFilesIn(dir);
        boolean found = false;
        for (File file : files) {
            found = true;
            openFileAsync(file, false);
        }
        if (!found) {
            Messages.showInfo("No files found",
                format("<html>No supported image files found in <b>%s</b>.", dir.getName()));
        }
    }

    public static void addAllImagesInDirAsLayers(File dir, Composition comp) {
        List<File> files = FileUtils.listSupportedInputFilesIn(dir);
        for (File file : files) {
            loadNewImageLayerAsync(file, comp);
        }
    }

    public static void exportLayersToPNGAsync(Composition comp) {
        assert calledOnEDT() : threadInfo();

        boolean okPressed = DirectoryChooser.selectOutputDir();
        if (!okPressed) {
            return;
        }

        CompletableFuture
            .supplyAsync(() -> exportLayersToPNG(comp), onIOThread)
            .thenAcceptAsync(numImg -> Messages.showInStatusBar(
                    "Saved " + numImg + " images to <b>" + Dirs.getLastSave() + "</b>")
                , onEDT)
            .exceptionally(Messages::showExceptionOnEDT);
    }

    private static int exportLayersToPNG(Composition comp) {
        assert calledOutsideEDT() : "on EDT";

        int numSavedImages = 0;
        for (int layerIndex = 0; layerIndex < comp.getNumLayers(); layerIndex++) {
            Layer layer = comp.getLayer(layerIndex);
            BufferedImage image = layer.asImage(true, false);
            if (image != null) {
                saveLayerImage(image, layer.getName(), layerIndex);
                numSavedImages++;
            }
        }
        return numSavedImages;
    }

    private static void saveLayerImage(BufferedImage image,
                                       String layerName,
                                       int layerIndex) {
        assert calledOutsideEDT() : "on EDT";

        File outputDir = Dirs.getLastSave();
        String fileName = format("%03d_%s.png", layerIndex, FileUtils.toFileName(layerName));
        File file = new File(outputDir, fileName);

        saveImageToFile(image, new SaveSettings(FileFormat.PNG, file));
    }

    public static void saveInAllFormats(Composition comp) {
        boolean canceled = !DirectoryChooser.selectOutputDir();
        if (canceled) {
            return;
        }
        File saveDir = Dirs.getLastSave();
        if (saveDir != null) {
            FileFormat[] fileFormats = FileFormat.values();
            for (FileFormat format : fileFormats) {
                File f = new File(saveDir, "all_formats." + format);
                var saveSettings = new SaveSettings(format, f);
                comp.saveAsync(saveSettings, false);
            }
        }
    }

    public static void saveJpegWithQuality(JpegInfo jpegInfo) {
        var comp = Views.getActiveComp();
        FileChoosers.saveWithSingleAllowedExtension(comp,
            comp.getFileNameWithExt("jpg"), jpegInfo, FileChoosers.jpegFilter);
    }

    static void saveToChosenFile(Composition comp, File file,
                                 Object extraInfo, String extension) {
        FileFormat format = FileFormat.fromExtension(extension).orElseThrow();
        SaveSettings settings;
        if (extraInfo != null) {
            // currently the only type of extra information
            assert format == FileFormat.JPG : "format = " + format + ", extraInfo = " + extraInfo;
            JpegInfo jpegInfo = (JpegInfo) extraInfo;
            settings = new JpegSettings(jpegInfo, file);
        } else {
            settings = new SaveSettings(format, file);
        }

        comp.saveAsync(settings, true);
    }

    public static void saveSVG(Shape shape, StrokeParam strokeParam, String suggestedFileName) {
        String svg = createSVGContent(shape, strokeParam);
        saveSVG(svg, suggestedFileName);
    }

    public static void saveSVG(String content, String suggestedFileName) {
        File file = FileChoosers.selectSaveFileForSpecificFormat(suggestedFileName, svgFilter);
        if (file == null) { // save file dialog cancelled
            return;
        }

        try (PrintWriter out = new PrintWriter(file, UTF_8)) {
            out.println(content);
        } catch (IOException e) {
            Messages.showException(e);
        }
        Messages.showFileSavedMessage(file);
    }

    private static String createSVGContent(Shape shape, StrokeParam strokeParam) {
        boolean exportFilled = false;
        if (strokeParam != null) {
            exportFilled = switch (strokeParam.getStrokeType()) {
                case ZIGZAG, CALLIGRAPHY, SHAPE, TAPERING, TAPERING_REV -> true;
                case BASIC, WOBBLE, CHARCOAL, BRISTLE, OUTLINE -> false;
            };
        }
        if (exportFilled) {
            shape = strokeParam.createStroke().createStrokedShape(shape);
        }
        String svgPath = Shapes.toSVGPath(shape);

        String svgFillRule = "nonzero";
        if (shape instanceof Path2D path) {
            svgFillRule = switch (path.getWindingRule()) {
                case Path2D.WIND_EVEN_ODD -> "evenodd";
                case Path2D.WIND_NON_ZERO -> "nonzero";
                default -> throw new IllegalStateException("Error: " + path.getWindingRule());
            };
        }

        String svgFillAttr = exportFilled ? "black" : "none";
        String svgStrokeAttr = exportFilled ? "none" : "black";
        String svgStrokeDescr = "";
        if (strokeParam != null && !exportFilled) {
            svgStrokeDescr = strokeParam.copyState().toSVGString();
        }

        return """
            %s
              <path d="%s" fill="%s" stroke="%s" fill-rule="%s" %s/>
            </svg>
            """.formatted(createSVGElement(), svgPath,
            svgFillAttr, svgStrokeAttr, svgFillRule, svgStrokeDescr);
    }

    public static String createSVGElement() {
        Canvas canvas = Views.getActiveComp().getCanvas();
        return "<svg width=\"%d\" height=\"%d\" xmlns=\"http://www.w3.org/2000/svg\">"
            .formatted(canvas.getWidth(), canvas.getHeight());
    }
}
