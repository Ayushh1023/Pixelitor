/*
 * Copyright 2022 Laszlo Balazs-Csiki and Contributors
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

package pixelitor.tools.shapes;

import pixelitor.filters.gui.EnumParam;
import pixelitor.tools.shapes.custom.RandomStarShape;
import pixelitor.tools.util.Drag;
import pixelitor.tools.util.DragDisplayType;
import pixelitor.utils.Shapes;
import pixelitor.utils.Utils;

import java.awt.BasicStroke;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.geom.*;

/**
 * The shapes types in the shapes tool
 */
public enum ShapeType {
    RECTANGLE("Rectangle", false, true, true) {
        @Override
        public Shape createShape(Drag drag, ShapeTypeSettings settings) {
            var rs = (RectangleSettings) settings;
            double radius = rs == null ? 0 : rs.getRadius();
            Rectangle2D r = drag.createPositiveImRect();
            if (radius == 0) {
                return r;
            } else {
                return new RoundRectangle2D.Double(r.getX(), r.getY(),
                    r.getWidth(), r.getHeight(), radius, radius);
            }
        }

        @Override
        public Shape createShape(double x, double y, double size) {
            return new Rectangle2D.Double(x, y, size, size);
        }

        @Override
        public DragDisplayType getDragDisplayType() {
            return DragDisplayType.WIDTH_HEIGHT;
        }

        @Override
        public RectangleSettings createSettings() {
            return new RectangleSettings();
        }
    }, ELLIPSE("Ellipse", false, false, false) {
        @Override
        public Shape createShape(Drag drag, ShapeTypeSettings settings) {
            Rectangle2D r = drag.createPositiveImRect();
            return new Ellipse2D.Double(r.getX(), r.getY(), r.getWidth(), r.getHeight());
        }

        @Override
        public Shape createShape(double x, double y, double size) {
            return new Ellipse2D.Double(x, y, size, size);
        }

        @Override
        public DragDisplayType getDragDisplayType() {
            return DragDisplayType.WIDTH_HEIGHT;
        }
    }, DIAMOND("Diamond", false, false, false) {
        @Override
        public Shape createShape(Drag drag, ShapeTypeSettings settings) {
            Rectangle2D r = drag.createPossiblyEmptyImRect();
            return Shapes.createDiamond(r.getX(), r.getY(), r.getWidth(), r.getHeight());
        }

        @Override
        public Shape createShape(double x, double y, double size) {
            return Shapes.createDiamond(x, y, size, size);
        }

        @Override
        public DragDisplayType getDragDisplayType() {
            return DragDisplayType.WIDTH_HEIGHT;
        }
    }, LINE("Line", true, true, true) {
        @Override
        public Shape createShape(Drag drag, ShapeTypeSettings settings) {
            var lineSettings = (LineSettings) settings;
            Stroke stroke;
            if (lineSettings != null) {
                stroke = lineSettings.getStroke();
            } else {
                stroke = new BasicStroke(5);
            }
            return stroke.createStrokedShape(drag.asLine());
        }

        @Override
        public Shape createShape(double x, double y, double size) {
            return new Rectangle2D.Double(x, y, size / 5.0, size);
        }

        @Override
        public DragDisplayType getDragDisplayType() {
            return DragDisplayType.ANGLE_DIST;
        }

        @Override
        public LineSettings createSettings() {
            return new LineSettings();
        }
    }, HEART("Heart", false, false, false) {
        @Override
        public Shape createShape(Drag drag, ShapeTypeSettings settings) {
            Rectangle2D r = drag.createPossiblyEmptyImRect();
            return Shapes.createHeart(r.getX(), r.getY(), r.getWidth(), r.getHeight());
        }

        @Override
        public Shape createShape(double x, double y, double size) {
            return Shapes.createHeart(x, y, size, size);
        }
    }, STAR("Star", false, true, false) {
        @Override
        public Shape createShape(Drag drag, ShapeTypeSettings settings) {
            StarSettings starSettings = (StarSettings) settings;
            int numBranches;
            double radiusRatio;
            if (starSettings != null) {
                numBranches = starSettings.getNumBranches();
                radiusRatio = starSettings.getRadiusRatio();
            } else {
                numBranches = 7;
                radiusRatio = 0.5;
            }

            Rectangle2D r = drag.createPositiveImRect();
            return Shapes.createStar(numBranches, r.getX(), r.getY(),
                r.getWidth(), r.getHeight(), radiusRatio);
        }

        @Override
        public Shape createShape(double x, double y, double size) {
            return Shapes.createStar(7, x, y, size, size, 0.5);
        }

        @Override
        public DragDisplayType getDragDisplayType() {
            return DragDisplayType.WIDTH_HEIGHT;
        }

        @Override
        public StarSettings createSettings() {
            return new StarSettings(7, 50);
        }
    }, RANDOM_STAR("Random Star", false, false, false) {
        private Drag lastDrag;

        @Override
        public Shape createShape(Drag drag, ShapeTypeSettings settings) {
            if (drag != lastDrag) {
                RandomStarShape.randomize();
            } else {
                // do not generate a completely new shape, only scale it
            }
            lastDrag = drag;

            Rectangle2D r = drag.createPossiblyEmptyImRect();
            return new RandomStarShape(r.getX(), r.getY(), r.getWidth(), r.getHeight());
        }

        @Override
        public Shape createShape(double x, double y, double size) {
            RandomStarShape.randomize();
            return new RandomStarShape(x, y, size, size);
        }

        @Override
        public DragDisplayType getDragDisplayType() {
            return DragDisplayType.WIDTH_HEIGHT;
        }
    }, ARROW("Arrow", true, false, false) {
        GeneralPath unitArrow = null;

        @Override
        public Shape createShape(Drag drag, ShapeTypeSettings settings) {
            if (unitArrow == null) {
                unitArrow = Shapes.createUnitArrow();
            }

            Rectangle2D r = drag.createPossiblyEmptyImRect();

            double distance = drag.calcImDist();
            var transform = AffineTransform.getTranslateInstance(r.getX(), r.getY());
            transform.scale(distance, distance); // originally it had a length of 1.0

            // rotate the arrow into the direction of the drag
            double angleInRadians = drag.getDrawAngle();
            double angle = Utils.atan2AngleToIntuitive(angleInRadians);
            angle += Math.PI / 2;
            transform.rotate(angle);

            return transform.createTransformedShape(unitArrow);
        }

        @Override
        public Shape createShape(double x, double y, double size) {
            double middleY = y + size / 2.0;
            Drag drag = new Drag(
                x,
                middleY,
                x + size,
                middleY);
            return createShape(drag, null);
        }

        @Override
        public DragDisplayType getDragDisplayType() {
            return DragDisplayType.ANGLE_DIST;
        }
    }, CAT("Cat", false, false, true) {
        @Override
        public Shape createShape(Drag drag, ShapeTypeSettings settings) {
            Rectangle2D r = drag.createPossiblyEmptyImRect();
            return Shapes.createCat(r.getX(), r.getY(), r.getWidth(), r.getHeight());
        }

        @Override
        public Shape createShape(double x, double y, double size) {
            return Shapes.createCat(x, y, size, size);
        }
    }, KIWI("Kiwi", false, false, false) {
        @Override
        public Shape createShape(Drag drag, ShapeTypeSettings settings) {
            Rectangle2D r = drag.createPossiblyEmptyImRect();
            return Shapes.createKiwi(r.getX(), r.getY(), r.getWidth(), r.getHeight());
        }

        @Override
        public Shape createShape(double x, double y, double size) {
            return Shapes.createKiwi(x, y, size, size);
        }
    }, BAT("Bat", false, false, true) {
        @Override
        public Shape createShape(Drag drag, ShapeTypeSettings settings) {
            Rectangle2D r = drag.createPossiblyEmptyImRect();
            return Shapes.createBat(r.getX(), r.getY(), r.getWidth(), r.getHeight());
        }

        @Override
        public Shape createShape(double x, double y, double size) {
            return Shapes.createBat(x, y, size, size);
        }
    }, RABBIT("Rabbit", false, false, false) {
        @Override
        public Shape createShape(Drag drag, ShapeTypeSettings settings) {
            Rectangle2D r = drag.createPossiblyEmptyImRect();
            return Shapes.createRabbit(r.getX(), r.getY(), r.getWidth(), r.getHeight());
        }

        @Override
        public Shape createShape(double x, double y, double size) {
            return Shapes.createRabbit(x, y, size, size);
        }
//    }, RND_ANIMAL_FACE("Random Animal Face", true, false) {
//        final int[] codePoints = {
//                0x1F428, // koala
//                0x1F42D, // mouse
//                0x1F42E, // cow
//                0x1F42F, // tiger
//                0x1F430, // rabbit
//                0x1F431, // cat
//              //  0x1F432, // dragon
//                0x1F434, // horse
//                0x1F435, // monkey
//                0x1F436, // dog
//                0x1F437, // pig
//                0x1F438, // frog
//                0x1F439, // hamster
//                0x1F43A, // wolf
//                0x1F43B, // bear
//                0x1F43C, // panda
//
//                // from here not even on windows
//                0x1F981, // lion
//                0x1F984, // unicorn
//                0x1F985, // eagle
//                0x1F98A, // fox
//                0x1F98C, // deer
//                0x1F98D, // gorilla
//                0x1F98F, // rhinoceros
//                0x1F993, // zebra
//                0x1F994, // hedgehog
//                0x1F99D, // raccoon
//        };
//        // it seems that on Linux not all code points are supported
//        final int[] supportedCodePoints = IntStream.of(codePoints)
//                .filter(UnicodeShapes::isSupported)
//                .toArray();
//
//        @Override
//        public Shape createShape(Drag drag) {
//            int codePoint = supportedCodePoints[Rnd.nextInt(supportedCodePoints.length)];
//            System.out.printf("ShapeType::createShape: codePoint = 0x%x%n", codePoint);
//            return UnicodeShapes.extract(codePoint,
//                    drag.getStartX(), drag.getStartY(), drag.getDX(), drag.getDY());
//        }
//
//        @Override
//        public Shape createShape(double x, double y, double diameter) {
//            int codePoint = supportedCodePoints[Rnd.nextInt(supportedCodePoints.length)];
//            return UnicodeShapes.extract(codePoint,
//                    x, y, diameter, diameter);
//        }
//    }, RND_CHESS("Random Chess Piece", true, false) {
//        final String[] chars = {"\u2654", "\u2655", "\u2656", "\u2657", "\u2658", "\u2659",
//                "\u265A", "\u265B", "\u265C", "\u265D", "\u265E", "\u265F"};
//
//        @Override
//        public Shape createShape(Drag drag) {
//            String uChar = Rnd.chooseFrom(chars);
//            return UnicodeShapes.extract(uChar,
//                    drag.getStartX(), drag.getStartY(), drag.getDX(), drag.getDY());
//        }
//
//        @Override
//        public Shape createShape(double x, double y, double diameter) {
//            String uChar = Rnd.chooseFrom(chars);
//            return UnicodeShapes.extract(uChar,
//                    x, y, diameter, diameter);
//        }
//    }, RND_ZODIAC("Random Zodiac Sign", true, false) {
//        final String[] chars = {"\u2648", "\u2649", "\u264A", "\u264B", "\u264C", "\u264D",
//                "\u264E", "\u264F", "\u2650", "\u2651", "\u2652", "\u2653"};
//
//        @Override
//        public Shape createShape(Drag drag) {
//            String uChar = Rnd.chooseFrom(chars);
//            return UnicodeShapes.extract(uChar,
//                    drag.getStartX(), drag.getStartY(), drag.getDX(), drag.getDY());
//        }
//
//        @Override
//        public Shape createShape(double x, double y, double diameter) {
//            String uChar = Rnd.chooseFrom(chars);
//            return UnicodeShapes.extract(uChar,
//                    x, y, diameter, diameter);
//        }
    };

    // the key can't be simpy "Shape", because
    // that key is used by the stroke settings
    public static final String PRESET_KEY = "ShapeType";

    private static final String NAME = "Shape";
    private final String guiName;

    private final boolean hasSettings;
    private final boolean areaProblem;

    // directional shapes are the arrow and line, where the
    // transform box is initialized at the angle of the shape
    private final boolean directional;

//    protected double x, y, width, height;

    ShapeType(String guiName, boolean directional, boolean hasSettings, boolean areaProblem) {
        this.guiName = guiName;
        this.directional = directional;
        this.hasSettings = hasSettings;
        this.areaProblem = areaProblem;
    }

    /**
     * The returned shapes must always be closed, so that they can be filled.
     */
    public abstract Shape createShape(Drag drag, ShapeTypeSettings settings);

    public abstract Shape createShape(double x, double y, double size);

//    /**
//     * Return the directional shape that would result
//     * from the given drag if it was horizontal
//     */
//    public Shape createHorizontalShape(Drag drag, ShapeTypeSettings settings) {
//        // overridden in directional types
//        assert !directional;
//        throw new UnsupportedOperationException("not directional");
//    }

    public DragDisplayType getDragDisplayType() {
        // overridden if necessary
        return DragDisplayType.NONE;
    }

    public boolean isDirectional() {
        return directional;
    }

    public boolean hasSettings() {
        return hasSettings;
    }

    /**
     * Return true if the shape could trigger https://bugs.openjdk.java.net/browse/JDK-6357341
     */
    public boolean hasAreaProblem() {
        return areaProblem;
    }

    public ShapeTypeSettings createSettings() {
        assert hasSettings() : "no settings for " + this;
        // should be overridden, if it has settings
        throw new UnsupportedOperationException("no settings for " + this);
    }

    public static EnumParam<ShapeType> asParam(ShapeType defaultType) {
        return asParam().withDefault(defaultType);
    }

    public static EnumParam<ShapeType> asParam() {
        return new EnumParam<>(NAME, ShapeType.class);
    }

    @Override
    public String toString() {
        return guiName;
    }
}
