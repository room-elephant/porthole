package com.roomelephant.porthole.core.service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;
import org.jspecify.annotations.NullMarked;

/**
 * Rebuilds an uploaded SVG from an allowlist, so only known drawing elements and attributes reach the disk. Anything
 * else is dropped: scripts, event handlers, embedded HTML, animation and references outside the document.
 *
 * <p>DTD support is off, so entities are never loaded or expanded. The JDK parser's limits reject extreme nesting.
 */
@NullMarked
final class SvgSanitizer {
    private static final String SVG_NAMESPACE = "http://www.w3.org/2000/svg";
    private static final String XLINK_NAMESPACE = "http://www.w3.org/1999/xlink";
    private static final String ROOT = "svg";
    private static final String STYLE = "style";
    private static final String IMAGE = "image";
    private static final String HREF = "href";

    private static final Set<String> DRAWABLE_ELEMENTS =
            Set.of("path", "rect", "circle", "ellipse", "line", "polyline", "polygon", "text", IMAGE, "use");

    private static final Set<String> STRUCTURE_ELEMENTS = Set.of(
            ROOT,
            STYLE,
            "g",
            "defs",
            "symbol",
            "switch",
            "title",
            "desc",
            "tspan",
            "textPath",
            "linearGradient",
            "radialGradient",
            "stop",
            "pattern",
            "clipPath",
            "mask",
            "marker",
            "filter",
            "feBlend",
            "feColorMatrix",
            "feComponentTransfer",
            "feComposite",
            "feDropShadow",
            "feFlood",
            "feFuncA",
            "feFuncB",
            "feFuncG",
            "feFuncR",
            "feGaussianBlur",
            "feMerge",
            "feMergeNode",
            "feMorphology",
            "feOffset");

    private static final Set<String> ATTRIBUTES = Set.of(
            "id",
            "class",
            STYLE,
            "viewBox",
            "preserveAspectRatio",
            "version",
            "transform",
            "x",
            "y",
            "x1",
            "y1",
            "x2",
            "y2",
            "cx",
            "cy",
            "r",
            "rx",
            "ry",
            "fx",
            "fy",
            "dx",
            "dy",
            "d",
            "width",
            "height",
            "points",
            "pathLength",
            "offset",
            "rotate",
            "fill",
            "fill-opacity",
            "fill-rule",
            "stroke",
            "stroke-dasharray",
            "stroke-dashoffset",
            "stroke-linecap",
            "stroke-linejoin",
            "stroke-miterlimit",
            "stroke-opacity",
            "stroke-width",
            "opacity",
            "color",
            "display",
            "visibility",
            "overflow",
            "clip",
            "clip-path",
            "clip-rule",
            "mask",
            "filter",
            "mix-blend-mode",
            "paint-order",
            "shape-rendering",
            "text-rendering",
            "image-rendering",
            "color-interpolation",
            "color-interpolation-filters",
            "vector-effect",
            "stop-color",
            "stop-opacity",
            "flood-color",
            "flood-opacity",
            "font-family",
            "font-size",
            "font-style",
            "font-weight",
            "letter-spacing",
            "word-spacing",
            "text-anchor",
            "text-decoration",
            "dominant-baseline",
            "alignment-baseline",
            "baseline-shift",
            "textLength",
            "lengthAdjust",
            "startOffset",
            "gradientUnits",
            "gradientTransform",
            "spreadMethod",
            "patternUnits",
            "patternContentUnits",
            "patternTransform",
            "clipPathUnits",
            "maskUnits",
            "maskContentUnits",
            "markerUnits",
            "markerWidth",
            "markerHeight",
            "refX",
            "refY",
            "orient",
            "marker-start",
            "marker-mid",
            "marker-end",
            "filterUnits",
            "primitiveUnits",
            "in",
            "in2",
            "result",
            "mode",
            "type",
            "values",
            "operator",
            "k1",
            "k2",
            "k3",
            "k4",
            "radius",
            "stdDeviation",
            "tableValues",
            "slope",
            "intercept",
            "amplitude",
            "exponent");

    /** CSS and attribute values that could fetch something from outside the document. */
    private static final Pattern EXTERNAL_REFERENCE = Pattern.compile(
            "\\\\|@import|@font-face|url\\(\\s*(?!['\"]?\\s*#)|image-set\\(|expression\\(", Pattern.CASE_INSENSITIVE);

    private static final Pattern EMBEDDED_RASTER =
            Pattern.compile("^data:image/(?:png|jpeg|jpg|webp);base64,[a-zA-Z0-9+/=\\s]*$");

    private SvgSanitizer() {}

    /** Returns the cleaned SVG, or empty if the input is not a well-formed SVG with something left to draw. */
    static Optional<byte[]> sanitize(byte[] bytes) {
        ByteArrayOutputStream output = new ByteArrayOutputStream(bytes.length);
        try {
            XMLStreamReader reader = newInputFactory().createXMLStreamReader(new ByteArrayInputStream(bytes));
            XMLStreamWriter writer =
                    XMLOutputFactory.newDefaultFactory().createXMLStreamWriter(output, StandardCharsets.UTF_8.name());
            boolean drawable = copy(reader, writer);
            writer.close();
            reader.close();
            return drawable ? Optional.of(output.toByteArray()) : Optional.empty();
        } catch (XMLStreamException _) {
            return Optional.empty();
        }
    }

    private static XMLInputFactory newInputFactory() {
        XMLInputFactory factory = XMLInputFactory.newDefaultFactory();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        factory.setProperty(XMLInputFactory.IS_REPLACING_ENTITY_REFERENCES, false);
        factory.setProperty(XMLInputFactory.IS_COALESCING, true);
        return factory;
    }

    /** Returns whether anything drawable was kept. Rejects a document whose root is not an SVG element. */
    private static boolean copy(XMLStreamReader reader, XMLStreamWriter writer) throws XMLStreamException {
        boolean drawable = false;
        boolean atRoot = true;
        int skippedDepth = 0;
        while (reader.hasNext()) {
            int event = reader.next();
            if (skippedDepth > 0) {
                skippedDepth += depthChange(event);
            } else if (event == XMLStreamConstants.START_ELEMENT) {
                if (atRoot && !isSvgRoot(reader)) {
                    return false;
                }
                if (!isAllowedElement(reader)) {
                    skippedDepth = 1;
                } else if (STYLE.equals(reader.getLocalName())) {
                    copyStyle(reader, writer);
                } else {
                    startElement(reader, writer, atRoot);
                    drawable |= DRAWABLE_ELEMENTS.contains(reader.getLocalName());
                }
                atRoot = false;
            } else if (event == XMLStreamConstants.END_ELEMENT) {
                writer.writeEndElement();
            } else if (event == XMLStreamConstants.CHARACTERS) {
                writer.writeCharacters(reader.getText());
            }
        }
        return drawable;
    }

    private static int depthChange(int event) {
        return switch (event) {
            case XMLStreamConstants.START_ELEMENT -> 1;
            case XMLStreamConstants.END_ELEMENT -> -1;
            default -> 0;
        };
    }

    private static boolean isSvgRoot(XMLStreamReader reader) {
        return SVG_NAMESPACE.equals(reader.getNamespaceURI()) && ROOT.equals(reader.getLocalName());
    }

    private static boolean isAllowedElement(XMLStreamReader reader) {
        String name = reader.getLocalName();
        return SVG_NAMESPACE.equals(reader.getNamespaceURI())
                && (STRUCTURE_ELEMENTS.contains(name) || DRAWABLE_ELEMENTS.contains(name));
    }

    private static void copyStyle(XMLStreamReader reader, XMLStreamWriter writer) throws XMLStreamException {
        String css = reader.getElementText();
        if (!EXTERNAL_REFERENCE.matcher(css).find()) {
            writer.writeStartElement(STYLE);
            writer.writeCharacters(css);
            writer.writeEndElement();
        }
    }

    private static void startElement(XMLStreamReader reader, XMLStreamWriter writer, boolean isRoot)
            throws XMLStreamException {
        String element = reader.getLocalName();
        writer.writeStartElement(element);
        if (isRoot) {
            writer.writeDefaultNamespace(SVG_NAMESPACE);
        }
        boolean hrefWritten = false;
        for (int i = 0; i < reader.getAttributeCount(); i++) {
            String name = reader.getAttributeLocalName(i);
            String namespace = reader.getAttributeNamespace(i);
            String value = reader.getAttributeValue(i);
            if (isHref(name, namespace)) {
                if (!hrefWritten && isSafeHref(element, value)) {
                    writer.writeAttribute(HREF, value.trim());
                    hrefWritten = true;
                }
            } else if (isPlain(namespace)
                    && ATTRIBUTES.contains(name)
                    && !EXTERNAL_REFERENCE.matcher(value).find()) {
                writer.writeAttribute(name, value);
            }
        }
    }

    private static boolean isHref(String name, String namespace) {
        return HREF.equals(name) && (isPlain(namespace) || XLINK_NAMESPACE.equals(namespace));
    }

    private static boolean isPlain(String namespace) {
        return namespace == null || namespace.isEmpty();
    }

    private static boolean isSafeHref(String element, String value) {
        String target = value.trim();
        return target.startsWith("#")
                || (IMAGE.equals(element) && EMBEDDED_RASTER.matcher(target).matches());
    }
}
