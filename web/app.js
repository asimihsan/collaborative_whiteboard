"use strict";

(function () {
    var endpoint = '';
    var local = (location.hostname === "localhost" || location.hostname === "127.0.0.1" || location.hostname.startsWith("192.168"));
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
        } catch (e) {
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

    async function setContentToRemote(identifier, sourceVersion, content, editor, onResponseCallback, isContentNewCallback) {
        const data = {
            "apiVersion": 1,
            "identifier": identifier,
            "sourceWhiteboardVersion": sourceVersion,
            "content": content
        };
        return postData(endpoint + '/api/set', data)
            .then((response) => {
                console.log("set response: " + response.ok);
                onResponseCallback();

                if (!response.ok) {
                    console.log("set failed!");
                    console.log(response);
                    PNotify.error({
                        text: "Failed to update whiteboard on server!"
                    });
                    return;
                }
                response.json().then(function (data) {
                    if (data === null) {
                        console.log("data unexpectedly null, ignoring");
                        return;
                    }
                    console.log(data); // JSON data parsed by `response.json()` call
                    if (isContentNewCallback(data)) {
                        updateLocalContent(data["content"], editor);
                    }
                });

            });
    }

    async function getContentFromRemote(identifier, editor, onResponseCallback, isContentNewCallback) {
        const data = {
            "apiVersion": 1,
            "identifier": identifier,
        };
        return postData(endpoint + '/api/get', data)
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
                    return;
                }

                response.json().then(function (data) {
                    if (data === null) {
                        console.log("data unexpectedly null, ignoring");
                        return;
                    }
                    console.log(data); // JSON data parsed by `response.json()` call
                    if (isContentNewCallback(data)) {
                        updateLocalContent(data["content"], editor);
                    }
                });
            });
    }

    function updateLocalContent(content, editor) {
        if (content === "" || content === null) {
            console.log("content is empty, not setting it");
            return;
        }
        const selectionCellIds = editor.graph.getSelectionCells().map((cell) => cell.id);

        console.log("updating local content");
        editor.undoManager.clear();
        var xml = mxUtils.parseXml(decompress(content));
        var dec = new mxCodec(xml.documentElement.ownerDocument);
        dec.decode(xml.documentElement, editor.graph.getModel());
        editor.undoManager.clear();

        let cellsToSelect = [];
        for (const cellId in editor.graph.model.cells) {
            if (editor.graph.model.cells.hasOwnProperty(cellId)) {
                if (selectionCellIds.includes(cellId)) {
                    const cell = editor.graph.model.cells[cellId];
                    cellsToSelect.push(cell);
                }
            }
        }
        editor.graph.setSelectionCells(cellsToSelect);
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
        identifier = 'abcdef2';
    }

    var lastGetVersion = -1;
    var lastGetContent = "";
    var focused = true;
    var refreshContentTimerId = -1;
    var refreshInterval = 1000;
    let editForTextInProgress = false;

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
        editor.graph.model.prefix = clientId + "_";
        editor.onbeforeunload = function() { };

        new EditorUi(editor);

        function onNotify(sender, event) {
            console.log('onNotify entry');
            console.log(sender);
            console.log(event);

            console.log('selection model');
            console.log(editor.graph.selectionModel);

            if (editForTextInProgress) {
                console.log("edit for text in progress so suppressing NOTIFY");
                return;
            }

            if (event !== null) {
                try {
                    var changes = event.getProperty('edit').changes;
                    if (changes.length === 1 && changes[0].child !== undefined && changes[0].child.value === "Text") {
                        console.log("suppressing set for Text");
                        return;
                    }
                } catch (error) {

                }
            }


            var enc = new mxCodec();
            var node = enc.encode(editor.graph.getModel());
            var xml = mxUtils.getPrettyXml(node);
            var xmlCompressed = compress(xml);

            if (xmlCompressed === lastGetContent) {
                console.log('change event but content is same');
                return;
            }
            console.log('change');

            var sourceVersion = lastGetVersion;
            setContentToRemote(identifier, sourceVersion, xmlCompressed, editor,
                function () {
                    if (refreshContentTimerId === -1 && focused) {
                        refreshContentTimerId = setInterval(refreshContent, refreshInterval);
                    }
                },
                function (remoteData) {
                    if (remoteData["currentNewestWhiteboardVersion"] > lastGetVersion) {
                        lastGetVersion = remoteData["currentNewestWhiteboardVersion"];
                        lastGetContent = remoteData["content"];
                    }
                    var didWeUpdateLatestVersion =
                        remoteData["requestSourceWhiteboardVersion"] === remoteData["existingNewestWhiteboardVersion"];
                    return !didWeUpdateLatestVersion;
                }
            );
        }

        editor.graph.addListener(mxEvent.EDITING_STARTED, mxUtils.bind(this, function (sender, event) {
            console.log("EDITING_STARTED");
            if (event.getProperty('cell').getValue() === "Text") {
                console.log("editing started for text");
                editForTextInProgress = true;
            }
        }));

        editor.graph.addListener(mxEvent.EDITING_STOPPED, mxUtils.bind(this, function (sender, event) {
            console.log("EDITING_STOPPED");
            if (editForTextInProgress) {
                editForTextInProgress = false;
                onNotify(sender, event);
            }
            editForTextInProgress = false;
        }));

        editor.graph.model.addListener(mxEvent.NOTIFY, mxUtils.bind(this, onNotify));

        // ------------------------------------------------------------

        function refreshContent() {
            console.log("refreshContent entry");
            if (!focused) {
                console.log("refreshContent no focus, skipping");
                return;
            }
            if (editForTextInProgress) {
                console.log("edit in progress so suppressing refreshContent");
                return;
            }

            getContentFromRemote(identifier, editor,
                function () {
                    if (refreshContentTimerId === -1 && focused) {
                        refreshContentTimerId = setInterval(refreshContent, refreshInterval);
                    }
                },
                function (remoteData) {
                    if (remoteData["whiteboardVersion"] > lastGetVersion) {
                        lastGetVersion = remoteData["whiteboardVersion"];
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

        window.onfocus = function () {
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

        window.onblur = function () {
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
