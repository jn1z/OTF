open module io.github.jn1z.otf {

    requires com.github.benmanes.caffeine;
    requires it.unimi.dsi.fastutil;
    requires net.automatalib.api;
    requires net.automatalib.common.smartcollection;
    requires net.automatalib.common.util;
    requires net.automatalib.core;
    requires net.automatalib.serialization.ba;
    requires net.automatalib.util;

    exports OTF;
    exports OTF.Compress;
    exports OTF.Model;
    exports OTF.Registry;
    exports OTF.Simulation;
}
