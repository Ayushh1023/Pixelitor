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

package pixelitor.filters.animation;

import pixelitor.io.FileChoosers;

import javax.swing.filechooser.FileNameExtensionFilter;
import java.io.File;

import static java.lang.String.format;

/**
 * The output type of the tweening animation
 */
public enum TweenOutputType {
    PNG_FILE_SEQUENCE("PNG File Sequence") {
        @Override
        AnimationWriter createWriter(File file, int delayMillis) {
            return new PNGFileSequenceWriter(file);
        }

        @Override
        public String validate(File output) {
            return checkDir(output, this);
        }

        @Override
        public boolean needsDirectory() {
            return true;
        }

        @Override
        public FileNameExtensionFilter getFileFilter() {
            return null;
        }
    }, ANIM_GIF("Animated GIF File") {
        @Override
        AnimationWriter createWriter(File file, int delayMillis) {
            return new AnimGIFWriter(file, delayMillis);
        }

        @Override
        public String validate(File output) {
            return checkFile(output, this, "GIF");
        }

        @Override
        public boolean needsDirectory() {
            return false;
        }

        @Override
        public FileNameExtensionFilter getFileFilter() {
            return FileChoosers.gifFilter;
        }
    };

    private final String guiName;

    TweenOutputType(String guiName) {
        this.guiName = guiName;
    }

    abstract AnimationWriter createWriter(File file, int delayMillis);

    /**
     * Returns the error message or null if the argument is OK as output
     */
    public abstract String validate(File output);

    public abstract boolean needsDirectory();

    private static String checkFile(File output,
                                    TweenOutputType type,
                                    String fileType) {
        if (output.exists()) {
            if (output.isDirectory()) {
                return format("%s is a folder." +
                        "<br>For the \"%s\" output type, " +
                        "select a (new or existing) %s file in an existing folder.",
                    output.getAbsolutePath(), type, fileType);
            }
        } else { // if it does not exist, we still expect the parent directory to exist
            File parentDir = output.getParentFile();
            if (parentDir == null) {
                return "Folder not found";
            }
            if (!parentDir.exists()) {
                return format("The folder %s of the %s file does not exist." +
                        "<br>For the \"%s\" output type, " +
                        "select a (new or existing) %s file in an existing folder.",
                    parentDir.getName(), output.getAbsolutePath(),
                    type, fileType);
            }
        }
        return null;
    }

    private static String checkDir(File output, TweenOutputType type) {
        // we expect it to be an existing directory
        if (!output.isDirectory()) {
            return format("\"<b>%s</b>\" is not a folder." +
                    "<br>For the \"%s\" output type, select an existing folder.",
                output.getAbsolutePath(), type);
        }
        if (!output.exists()) {
            return output.getAbsolutePath() + " does not exist.";
        }
        return null;
    }

    public abstract FileNameExtensionFilter getFileFilter();

    @Override
    public String toString() {
        return guiName;
    }
}
