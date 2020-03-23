(function () {
    var endpoint = '';
    var local = (location.hostname === "localhost" || location.hostname === "127.0.0.1");
    if (local) {
        endpoint = 'https://whiteboard-preprod.ihsan.io';
    }

    // See: https://developer.mozilla.org/en-US/docs/Web/API/Web_Storage_API/Using_the_Web_Storage_API#Feature-detecting_localStorage
    function storageAvailable(type) {
        var storage;
        try {
            storage = window[type];
            var x = '__storage_test__';
            storage.setItem(x, x);
            storage.removeItem(x);
            return true;
        }
        catch(e) {
            return e instanceof DOMException && (
                // everything except Firefox
                e.code === 22 ||
                // Firefox
                e.code === 1014 ||
                // test name field too, because code might not be present
                // everything except Firefox
                e.name === 'QuotaExceededError' ||
                // Firefox
                e.name === 'NS_ERROR_DOM_QUOTA_REACHED') &&
                // acknowledge QuotaExceededError only if there's something already stored
                (storage && storage.length !== 0);
        }
    }

    function guid() {
        function s4() {
            return Math.floor((1 + Math.random()) * 0x10000)
                .toString(16)
                .substring(1);
        }
        return s4() + s4() + '-' + s4() + '-' + s4() + '-' +
            s4() + '-' + s4() + s4() + s4();
    }

    // See Graph.js compress() function.
    // - Replaced the pako distributed with the latest non-minified version.
    // - Skipped the URI encoding and zapGremlins parts.
    function compress(text) {
        var tmp = pako.deflate(text, {level: 9, memlevel: 9, to: 'string'});
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
        var mode = 'same-origin';
        if (local) {
            mode = 'cors';
        }

        // Default options are marked with *
        const response = await fetch(url, {
            method: 'POST', // *GET, POST, PUT, DELETE, etc.
            mode: mode, // no-cors, *cors, same-origin
            cache: 'no-cache', // *default, no-cache, reload, force-cache, only-if-cached
            credentials: 'same-origin', // include, *same-origin, omit
            headers: {
                'Content-Type': 'application/json'
            },
            redirect: 'error', // manual, *follow, error
            referrerPolicy: 'no-referrer', // no-referrer, *client
            body: JSON.stringify(data) // body data type must match "Content-Type" header
        });
        return response;
    }

    function setContentToRemote(identifier, content, editor, onResponseCallback, isContentNewCallback) {
        const data = {
            "identifier": identifier,
            "content": content
        };
        postData(endpoint + '/api/set', data)
            .then((response) => {
                console.log("set response: " + response.ok);
                onResponseCallback();

                if (!response.ok) {
                    console.log("set failed!");
                    console.log(response);
                    PNotify.error({
                       text: "Failed to update whiteboard on server!"
                    });
                    editor.graph.setEnabled(true);
                    return;
                }
                response.json().then(function(data) {
                    if (data === null) {
                        console.log("data unexpectedly null, ignoring");
                        editor.graph.setEnabled(true);
                        return;
                    }
                    console.log(data); // JSON data parsed by `response.json()` call
                    if (isContentNewCallback(data)) {
                        updateLocalContent(data["content"], editor);
                        editor.graph.setEnabled(true);
                    }
                });

            });
    }

    function getContentFromRemote(identifier, editor, onResponseCallback, isContentNewCallback) {
        const data = {
            "identifier": identifier,
        };
        postData(endpoint + '/api/get', data)
            .then((response) => {
                console.log("get response: " + response.ok);
                onResponseCallback();

                PNotify.closeAll();
                if (!response.ok) {
                    console.log("get failed!");
                    console.log(response);
                    PNotify.error({
                        text: "Failed to get whiteboard from server!"
                    });
                    editor.graph.setEnabled(true);
                    return;
                }

                response.json().then(function(data) {
                    if (data === null) {
                        console.log("data unexpectedly null, ignoring");
                        editor.graph.setEnabled(true);
                        return;
                    }
                    console.log(data); // JSON data parsed by `response.json()` call
                    editor.graph.setEnabled(true);
                    if (isContentNewCallback(data)) {
                        updateLocalContent(data["content"], editor);
                        editor.graph.setEnabled(true);
                    }
                });
            });
    }

    function updateLocalContent(content, editor) {
        if (content === "" || content === null) {
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
    if (local) {
        identifier = 'abcdef';
    }

    var lastGetVersion = -1;
    var lastGetContent = "";
    var suppressNextChangeEvent = false;
    var focused = true;
    var refreshContentTimerId = -1;
    var refreshInterval = 1000;

    // clientId is a random UUID that we prefix MxGraph cells with. This is part of the conflict-resolution done
    // on the server side. Since we expect users to refresh the browser sometimes we cache this in session storage
    // to try and keep it the same.
    var clientId;

    if (!storageAvailable('sessionStorage') || !sessionStorage.getItem('clientId')) {
        clientId = guid();
        if (storageAvailable('sessionStorage')) {
            sessionStorage.setItem('clientId', clientId);
        }
    } else {
        clientId = sessionStorage.getItem('clientId');
    }

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
        editor.graph.setEnabled(false);
        editor.graph.model.prefix = clientId + "_";

        new EditorUi(editor);

        // ------------------------------------------------------------
        //	This is how to get the XML of the whole document given a change
        //	See: Graph.js line 3392
        //	See: mxGraphModel header comment
        // ------------------------------------------------------------
        // editor.graph.model.addListener(mxEvent.CHANGE, mxUtils.bind(this, function (sender, event) {
        editor.graph.model.addListener(mxEvent.CHANGE, mxUtils.bind(this, function () {
            if (suppressNextChangeEvent) {
                console.log('change, but suppressed');
                suppressNextChangeEvent = false;
                return;
            }
            console.log('change');
            editor.graph.setEnabled(false);

            var enc = new mxCodec();
            var node = enc.encode(editor.graph.getModel());
            var xml = mxUtils.getPrettyXml(node);
            var xmlCompressed = compress(xml);
            setContentToRemote(identifier, xmlCompressed, editor,
                function() {
                    if (refreshContentTimerId === -1 && focused) {
                        refreshContentTimerId = setInterval(refreshContent, refreshInterval);
                    }
                },
                function(remoteData) {
                if (remoteData["content"] !== lastGetContent) {
                    lastGetVersion = remoteData["version"];
                    lastGetContent = remoteData["content"];
                    suppressNextChangeEvent = true;
                    return true;
                }
                return false;
            });

            // var changes = event.getProperty('edit').changes;
            // console.log("changes");
            // console.log(changes);
        }));
        // ------------------------------------------------------------

        function refreshContent() {
            console.log("refreshContent entry");
            if (!focused) {
                console.log("refreshContent no focus, skipping");
                return;
            }
            getContentFromRemote(identifier, editor,
                function() {
                    if (refreshContentTimerId === -1 && focused) {
                        refreshContentTimerId = setInterval(refreshContent, refreshInterval);
                    }
                },
                function(remoteData) {
                if (remoteData["content"] !== lastGetContent) {
                    lastGetVersion = remoteData["version"];
                    lastGetContent = remoteData["content"];
                    return true;
                }
                return false;
            });
        }

        PNotify.info({
            text: "First-time loading whiteboard content from server..."
        });
        refreshContent();

        window.onfocus = function() {
            console.log("gained focus, ensuring refresh timer is running");
            focused = true;
            if (refreshContentTimerId > 0) {
                clearInterval(refreshContentTimerId);
                refreshContentTimerId = -1;
            }
            if (refreshContentTimerId === -1) {
                refreshContentTimerId = setInterval(refreshContent, refreshInterval);
            }
        };

        window.onblur = function() {
            console.log("lost focus, ensuring refresh timer is halted");
            focused = false;
            if (refreshContentTimerId > 0) {
                clearInterval(refreshContentTimerId);
                refreshContentTimerId = -1;
            }
        }

    }, function () {
        document.body.innerHTML = '<center style="margin-top:10%;">Error loading resource files. Please check browser console.</center>';
    });
})();
