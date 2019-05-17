
// IE11 doesn't have this
if (!FileReader.prototype.readAsBinaryString) {
    FileReader.prototype.readAsBinaryString = function (fileData) {
        var binary = "";
        var pt = this;
        var reader = new FileReader();

        reader.onload = function (e) {
            var bytes = new Uint8Array(reader.result);

            for (var i = 0; i < bytes.byteLength; i++)
                binary += String.fromCharCode(bytes[i]);

            //pt.result - readonly so assign content to another property
            pt.content = binary;
            pt.onload();
        }

        reader.readAsArrayBuffer(fileData);
    }
}


