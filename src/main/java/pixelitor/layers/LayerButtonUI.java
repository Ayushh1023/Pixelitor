/*
 * Copyright 2020 Laszlo Balazs-Csiki and Contributors
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

package pixelitor.layers;

import pixelitor.gui.utils.Themes;

import javax.swing.*;
import javax.swing.plaf.basic.BasicToggleButtonUI;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;

import static java.awt.RenderingHints.KEY_ANTIALIASING;
import static java.awt.RenderingHints.VALUE_ANTIALIAS_ON;

/**
 * A UI class for {@link LayerButton}
 */
public class LayerButtonUI extends BasicToggleButtonUI {
    @Override
    protected void paintButtonPressed(Graphics g, AbstractButton b) {
        Graphics2D g2 = (Graphics2D) g;

        // save Graphics settings
        Color oldColor = g.getColor();
        Object oldAA = g2.getRenderingHint(KEY_ANTIALIASING);

        // paint a rounded rectangle with the selection color
        // on the selected layer button
        g2.setRenderingHint(KEY_ANTIALIASING, VALUE_ANTIALIAS_ON);
        if (Themes.getCurrent().isDark()) {
            g.setColor(LayerButton.SELECTED_DARK_COLOR);
        } else {
            g.setColor(LayerButton.SELECTED_COLOR);
        }
        g.fillRoundRect(0, 0, b.getWidth(), b.getHeight(), 10, 10);

        // restore Graphics settings
        g.setColor(oldColor);
        g2.setRenderingHint(KEY_ANTIALIASING, oldAA);
    }
}
