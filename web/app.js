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

    // https://developer.mozilla.org/en-US/docs/Web/API/Fetch_API/Using_Fetch
    async function postData(url = '', data = {}) {
        // Default options are marked with *
        const response = await fetch(url, {
            method: 'POST', // *GET, POST, PUT, DELETE, etc.
            mode: 'same-origin', // no-cors, *cors, same-origin
            cache: 'no-cache', // *default, no-cache, reload, force-cache, only-if-cached
            credentials: 'same-origin', // include, *same-origin, omit
            headers: {
                'Content-Type': 'application/json'
            },
            redirect: 'error', // manual, *follow, error
            referrerPolicy: 'no-referrer', // no-referrer, *client
            body: JSON.stringify(data) // body data type must match "Content-Type" header
        });
        return await response.json(); // parses JSON response into native JavaScript objects
    }

    function setContentToRemote(identifier, content, editor, isContentNewCallback) {
        const data = {
            "identifier": identifier,
            "content": content
        };
        postData('/api/set', data)
            .then((data) => {
                console.log("set response");
                console.log(data); // JSON data parsed by `response.json()` call
                if (isContentNewCallback(data)) {
                    updateLocalContent(data["content"], editor);
                }
            });
    }

    function getContentFromRemote(identifier, editor, isContentNewCallback) {
        const data = {
            "identifier": identifier,
        };
        postData('/api/get', data)
            .then((data) => {
                console.log("get response");
                console.log(data); // JSON data parsed by `response.json()` call
                if (isContentNewCallback(data)) {
                    updateLocalContent(data["content"], editor);
                }
            });
    }

    function updateLocalContent(content, editor) {
        if (content === "") {
            console.log("content is empty, not setting it");
            return;
        }
        editor.undoManager.clear();
        var xml = mxUtils.parseXml(decompress(content));
        var dec = new mxCodec(xml.documentElement.ownerDocument);
        dec.decode(xml.documentElement, editor.graph.getModel());
        editor.undoManager.clear();
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

    var identifier = window.location.href.split("/").pop();
    var lastGetVersion = -1;
    var lastGetContent = "";

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
            setContentToRemote(identifier, xmlCompressed, editor, function(remoteData) {
                if (remoteData["content"] !== lastGetContent) {
                    lastGetVersion = remoteData["version"];
                    lastGetContent = remoteData["content"];
                    return true;
                }
                return false;
            });

            // var changes = event.getProperty('edit').changes;
            // console.log("changes");
            // console.log(changes);
        }));
        // ------------------------------------------------------------

        getContentFromRemote(identifier, editor, function(remoteData) {
            if (remoteData["content"] !== lastGetContent) {
                lastGetVersion = remoteData["version"];
                lastGetContent = remoteData["content"];
                return true;
            }
            return false;
        });

    }, function () {
        document.body.innerHTML = '<center style="margin-top:10%;">Error loading resource files. Please check browser console.</center>';
    });
})();
