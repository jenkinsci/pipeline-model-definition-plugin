Behaviour.specify(".directive-generator-button-reference-holder", 'prototype', 0, function (e) {
    var url = e.getAttribute('data-fullurl');
    var id = e.getAttribute('data-id');
    var button = document.getElementById('prototypeButton_'+id);

    button.onclick = function(el) {
       handlePrototype(url,id);
       return false;
    }
});
function handlePrototype(url,id) {
    buildFormTree(document.forms.config);
    var json = JSON.stringify(JSON.parse(document.forms.config.elements.json.value).prototype);
    if (!json) {
    return; // just a separator
    }
    fetch(url, {
        method: 'post',
        headers: crumb.wrap({
            "Content-Type": "application/x-www-form-urlencoded",
        }),
        body: new URLSearchParams({
            json: json,
        }),
    }).then((rsp) => {
        if (rsp.ok) {
            rsp.text().then((responseText) => {
                document.getElementById('prototypeText_'+id).value = responseText;
                copybutton = document.querySelector('.copyPrototypeButton_'+id);
                copybutton.setAttribute("text", responseText);
                copybutton.classList.remove('jenkins-hidden');
            });
        }
    });
    }