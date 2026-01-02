/**
 * ExpiryGuard - Certificate Parser
 * Parses .p12, .pem, .cer files in the browser using node-forge
 */

document.addEventListener('DOMContentLoaded', function () {
    const dropZone = document.getElementById('certDropZone');
    const fileInput = document.getElementById('certFileInput');
    const dropZoneContent = document.getElementById('dropZoneContent');
    const successContent = document.getElementById('successContent');
    const parseError = document.getElementById('parseError');
    const nameInput = document.getElementById('name');
    const expiryInput = document.getElementById('expiryDate');
    const notesInput = document.getElementById('notes');

    // Reset form when modal opens
    document.getElementById('addSecretModal').addEventListener('show.bs.modal', function () {
        resetForm();
    });

    function resetForm() {
        dropZone.className = 'cert-drop-zone';
        dropZoneContent.style.display = 'block';
        successContent.style.display = 'none';
        parseError.style.display = 'none';
        nameInput.value = '';
        expiryInput.value = '';
        notesInput.value = '';
        fileInput.value = '';
    }

    // Click to browse
    dropZone.addEventListener('click', () => fileInput.click());

    // Drag and drop events
    dropZone.addEventListener('dragover', function (e) {
        e.preventDefault();
        dropZone.classList.add('dragover');
    });

    dropZone.addEventListener('dragleave', function (e) {
        e.preventDefault();
        dropZone.classList.remove('dragover');
    });

    dropZone.addEventListener('drop', function (e) {
        e.preventDefault();
        dropZone.classList.remove('dragover');
        const files = e.dataTransfer.files;
        if (files.length > 0) {
            handleFile(files[0]);
        }
    });

    fileInput.addEventListener('change', function (e) {
        if (e.target.files.length > 0) {
            handleFile(e.target.files[0]);
        }
    });

    function handleFile(file) {
        const validExtensions = ['.p12', '.pem', '.cer', '.crt', '.pfx'];
        const fileExt = '.' + file.name.split('.').pop().toLowerCase();

        if (!validExtensions.includes(fileExt)) {
            showError('Unsupported file type. Please use .p12, .pem, or .cer files.');
            return;
        }

        // Guard against huge files
        if (file.size > 5 * 1024 * 1024) {
            showError('File too large to parse in browser.');
            return;
        }

        const reader = new FileReader();
        reader.onload = function (e) {
            parseCertificate(e.target.result, file.name, fileExt);
        };

        // Read binary files as ArrayBuffer, text files as text
        if (fileExt === '.p12' || fileExt === '.pfx' || fileExt === '.cer') {
            reader.readAsArrayBuffer(file);
        } else {
            reader.readAsText(file);
        }
    }

    function parseCertificate(data, filename, fileExt) {
        let cert;

        try {
            if (fileExt === '.p12' || fileExt === '.pfx') {
                // Convert ArrayBuffer → forge binary string
                const bytes = new Uint8Array(data);
                let binary = '';
                for (let i = 0; i < bytes.length; i++) {
                    binary += String.fromCharCode(bytes[i]);
                }

                // Parse ASN.1 from binary string
                const asn1 = forge.asn1.fromDer(binary);

                // Attempt parsing without password first
                let p12;
                try {
                    p12 = forge.pkcs12.pkcs12FromAsn1(asn1, '');
                } catch (e) {
                    // Prompt once for password if empty password fails
                    const password = prompt('Enter certificate password (leave blank if none):') || '';
                    try {
                        p12 = forge.pkcs12.pkcs12FromAsn1(asn1, password);
                    } catch (inner) {
                        showError('Couldn\'t read this certificate. You can still enter details manually.');
                        return;
                    }
                }

                // Extract the first certificate only
                const bags = p12.getBags({ bagType: forge.pki.oids.certBag });
                const bagArray = bags[forge.pki.oids.certBag];
                if (!bagArray || bagArray.length === 0) {
                    throw new Error('No certificate found in P12 file');
                }
                cert = bagArray[0].cert;
            } else if (fileExt === '.cer') {
                // Handle DER format (.cer files)
                const binaryString = String.fromCharCode.apply(null, new Uint8Array(data));
                const buffer = forge.util.createBuffer(binaryString, 'raw');
                try {
                    const asn1 = forge.asn1.fromDer(buffer);
                    cert = forge.pki.certificateFromAsn1(asn1);
                } catch (derError) {
                    // Try as PEM if DER fails
                    const textData = new TextDecoder().decode(data);
                    if (textData.includes('-----BEGIN CERTIFICATE-----')) {
                        cert = forge.pki.certificateFromPem(textData);
                    } else {
                        throw derError;
                    }
                }
            } else {
                // Handle PEM files
                if (typeof data === 'string' && data.includes('-----BEGIN CERTIFICATE-----')) {
                    cert = forge.pki.certificateFromPem(data);
                } else {
                    throw new Error('Invalid PEM format');
                }
            }

            if (!cert) {
                throw new Error('Could not parse certificate');
            }

            // Extract metadata
            const commonName = getCommonName(cert);
            const expiry = new Date(cert.validity.notAfter);
            expiry.setHours(0, 0, 0, 0);

            // Auto-fill form fields
            nameInput.value = commonName || filename.replace(/\.[^/.]+$/, '');
            expiryInput.value = formatDateForInput(expiry);
            notesInput.value = `Imported from ${filename}`;

            showSuccess();

        } catch (error) {
            showError('Couldn\'t read this certificate. You can still enter details manually.');
        }
    }

    function getCommonName(cert) {
        try {
            const subject = cert.subject;
            for (let i = 0; i < subject.attributes.length; i++) {
                const attr = subject.attributes[i];
                if (attr.name === 'commonName' || attr.shortName === 'CN') {
                    return attr.value;
                }
            }
            // Fallback to organization
            for (let i = 0; i < subject.attributes.length; i++) {
                const attr = subject.attributes[i];
                if (attr.name === 'organizationName' || attr.shortName === 'O') {
                    return attr.value;
                }
            }
            return null;
        } catch (e) {
            return null;
        }
    }

    function formatDateForInput(date) {
        const year = date.getFullYear();
        const month = String(date.getMonth() + 1).padStart(2, '0');
        const day = String(date.getDate()).padStart(2, '0');
        return `${year}-${month}-${day}`;
    }

    function showSuccess() {
        dropZone.classList.add('success');
        dropZoneContent.style.display = 'none';
        successContent.style.display = 'block';
        parseError.style.display = 'none';
    }

    function showError(message) {
        dropZone.className = 'cert-drop-zone';
        parseError.style.display = 'block';
        if (message) {
            parseError.textContent = message;
        }
    }
});
