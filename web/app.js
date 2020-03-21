(function () {
    // See Graph.js compress() function.
    // - Replaced the pako distributed with the latest non-minified version.
    // - Skipped the URI encoding and zapGremlins parts.
    function compress(text) {
        var tmp = pako.deflate(text, {to: 'string'});
        tmp = Base64.encode(tmp, true);
        return tmp;
    }

    function decompress(binary) {
        var tmp = Base64.decode(binary, true);
        tmp = pako.inflate(tmp, {to: 'string'});
        return tmp;
    }

    var editorUiInit = EditorUi.prototype.init;

    EditorUi.prototype.init = function () {
        editorUiInit.apply(this, arguments);
        this.actions.get('new').setEnabled(false);
        this.actions.get('open').setEnabled(false);
        this.actions.get('import').setEnabled(false);
        this.actions.get('save').setEnabled(false);
        this.actions.get('saveAs').setEnabled(false);
        this.actions.get('export').setEnabled(false);
    };

    // Adds required resources (disables loading of fallback properties, this can only
    // be used if we know that all keys are defined in the language specific file)
    mxResources.loadDefaultBundle = false;
    var bundle = mxResources.getDefaultBundle(RESOURCE_BASE, mxLanguage) ||
        mxResources.getSpecialBundle(RESOURCE_BASE, mxLanguage);

    // Fixes possible asynchronous requests
    mxUtils.getAll([bundle, STYLE_PATH + '/default.xml'], function (xhr) {
        // Adds bundle text to resources
        mxResources.parse(xhr[0].getText());

        // Configures the default graph theme
        var themes = new Object();
        themes[Graph.prototype.defaultThemeName] = xhr[1].getDocumentElement();

        // Main

        // Chromeless true is good for printing out or exporting.
        var chromeless = false;
        var editor = new Editor(chromeless, themes);
        new EditorUi(editor);

        // ------------------------------------------------------------
        //	This is how to get the XML of the whole document given a change
        //	See: Graph.js line 3392
        //	See: mxGraphModel header comment
        // ------------------------------------------------------------
        // editor.graph.model.addListener(mxEvent.CHANGE, mxUtils.bind(this, function (sender, event) {
        editor.graph.model.addListener(mxEvent.CHANGE, mxUtils.bind(this, function () {
            console.log('change');
            var enc = new mxCodec();
            var node = enc.encode(editor.graph.getModel());
            var xml = mxUtils.getPrettyXml(node);
            var xmlCompressed = compress(xml);
            // console.log("new proposed document");
            // console.log(xmlCompressed);

            // var changes = event.getProperty('edit').changes;
            // console.log("changes");
            // console.log(changes);

        }));
        // ------------------------------------------------------------

        // ------------------------------------------------------------
        //	This is an example of overwriting the current state with some whole document.
        // ------------------------------------------------------------
        editor.undoManager.clear();
        var exampleSerialized = "eJy9kU0OgjAQRveeopkL8GMUEqgbF6xceYKGjrRJsaSMUm5vEWI0xI0LV/My7cz3kilbXznRqZOVaA4bxkpnLU0QsPVHNIZpySGGaN1MgHXC4ZW+vGfA7sLckAOwnkYTYFCa8NyJGvkQcgtFreFJIfoOa+IX7VEWYQodof8ISGDe/0yo0LZIbmThT7qPgY0ctmmog5akOOQBFepG0cyi59AsMy/RaDZda+d/0c4W7d3v2mU0H2vqvV/xAbZfjEI=";
        var xml = mxUtils.parseXml(decompress(exampleSerialized));
        var dec = new mxCodec(xml.documentElement.ownerDocument);
        dec.decode(xml.documentElement, editor.graph.getModel());
        editor.undoManager.clear();

        // ------------------------------------------------------------

    }, function () {
        document.body.innerHTML = '<center style="margin-top:10%;">Error loading resource files. Please check browser console.</center>';
    });
})();
