(() => {
    const saveLinks = Array.from(document.querySelectorAll("[data-photo-save]"));
    const selectInputs = Array.from(document.querySelectorAll("[data-photo-select]"));
    const bulkSaveButtons = Array.from(document.querySelectorAll("[data-bulk-save]"));
    const bulkClearButtons = Array.from(document.querySelectorAll("[data-bulk-clear]"));
    const selectionCounts = Array.from(document.querySelectorAll("[data-selection-count]"));
    const selectionToggleButtons = Array.from(document.querySelectorAll("[data-selection-toggle]"));

    function supportsFileShare() {
        return typeof navigator.share === "function"
            && typeof navigator.canShare === "function"
            && typeof File === "function";
    }

    function isAppleTouchDevice() {
        return /iPad|iPhone|iPod/.test(navigator.userAgent)
            || (navigator.platform === "MacIntel" && navigator.maxTouchPoints > 1);
    }

    function fallbackToBrowser(downloadUrl, shareUrl) {
        window.location.href = isAppleTouchDevice() && shareUrl ? shareUrl : downloadUrl;
    }

    async function savePhoto(event) {
        const link = event.currentTarget;
        const downloadUrl = link.dataset.saveUrl || link.href;
        const shareUrl = link.dataset.shareUrl || downloadUrl;
        const filename = link.dataset.saveFilename || link.getAttribute("download") || "crowdcam-photo.jpg";
        if (!downloadUrl) return;

        if (!supportsFileShare()) {
            if (isAppleTouchDevice() && shareUrl) {
                event.preventDefault();
                fallbackToBrowser(downloadUrl, shareUrl);
            }
            return;
        }

        event.preventDefault();
        link.setAttribute("aria-busy", "true");
        let shouldFallback = true;

        try {
            const response = await fetch(shareUrl, {credentials: "same-origin"});
            if (!response.ok) throw new Error("Photo download failed.");

            const blob = await response.blob();
            const contentType = blob.type || response.headers.get("Content-Type") || "image/jpeg";
            const file = new File([blob], filename, {type: contentType});
            if (navigator.canShare({files: [file]})) {
                await navigator.share({files: [file], title: filename});
                shouldFallback = false;
                return;
            }
        } catch (error) {
            if (error?.name === "AbortError") {
                shouldFallback = false;
            }
        } finally {
            link.removeAttribute("aria-busy");
        }

        if (shouldFallback) {
            fallbackToBrowser(downloadUrl, shareUrl);
        }
    }

    function selectedPhotos() {
        return selectInputs.filter((input) => input.checked);
    }

    function selectionItemFor(input) {
        return input.closest(".gallery-item, .photo-card, tr");
    }

    function isSelectionMode() {
        return document.body.classList.contains("selection-mode");
    }

    function updateSelectedItems() {
        selectInputs.forEach((input) => {
            selectionItemFor(input)?.classList.toggle("selected", input.checked);
        });
    }

    function clearSelection() {
        selectInputs.forEach((input) => {
            input.checked = false;
        });
        updateSelectedItems();
    }

    function setSelectionMode(enabled) {
        document.body.classList.toggle("selection-mode", enabled);
        selectionToggleButtons.forEach((button) => {
            button.textContent = enabled ? "Cancel" : "Select";
            button.setAttribute("aria-pressed", String(enabled));
        });
        if (!enabled) {
            clearSelection();
        }
        updateSelectionState();
    }

    function updateSelectionState() {
        const selectionMode = isSelectionMode();
        const selectedCount = selectedPhotos().length;
        selectionCounts.forEach((count) => {
            count.textContent = `${selectedCount} selected`;
            count.hidden = !selectionMode;
        });
        bulkClearButtons.forEach((button) => {
            button.hidden = !selectionMode;
            button.disabled = selectedCount === 0;
        });
        bulkSaveButtons.forEach((button) => {
            button.hidden = !selectionMode;
            button.disabled = selectedCount === 0;
        });
        updateSelectedItems();
    }

    function archiveUrl(button, selected) {
        const url = new URL(button.dataset.bulkDownloadUrl, window.location.href);
        selected.forEach((input) => {
            url.searchParams.append("photoIds", input.value);
        });
        return url.toString();
    }

    async function shareSelectedPhotos(selected) {
        const files = [];
        for (const input of selected) {
            const shareUrl = input.dataset.shareUrl || input.dataset.saveUrl;
            const filename = input.dataset.saveFilename || "crowdcam-photo.jpg";
            const response = await fetch(shareUrl, {credentials: "same-origin"});
            if (!response.ok) throw new Error("Photo download failed.");

            const blob = await response.blob();
            const contentType = blob.type || response.headers.get("Content-Type") || "image/jpeg";
            files.push(new File([blob], filename, {type: contentType}));
        }

        if (!navigator.canShare({files})) return false;
        await navigator.share({files, title: "CrowdCam photos"});
        return true;
    }

    async function saveSelectedPhotos(event) {
        const button = event.currentTarget;
        const selected = selectedPhotos();
        if (selected.length === 0) return;

        const fallbackUrl = archiveUrl(button, selected);
        if (!supportsFileShare()) {
            window.location.href = fallbackUrl;
            return;
        }

        button.setAttribute("aria-busy", "true");
        let shouldFallback = true;

        try {
            shouldFallback = !(await shareSelectedPhotos(selected));
        } catch (error) {
            if (error?.name === "AbortError") {
                shouldFallback = false;
            }
        } finally {
            button.removeAttribute("aria-busy");
        }

        if (shouldFallback) {
            window.location.href = fallbackUrl;
        }
    }

    saveLinks.forEach((link) => {
        link.addEventListener("click", savePhoto);
    });

    selectInputs.forEach((input) => {
        input.addEventListener("change", updateSelectionState);
    });

    selectionToggleButtons.forEach((button) => {
        button.addEventListener("click", () => setSelectionMode(!isSelectionMode()));
    });

    bulkSaveButtons.forEach((button) => {
        button.addEventListener("click", saveSelectedPhotos);
    });

    bulkClearButtons.forEach((button) => {
        button.addEventListener("click", () => {
            clearSelection();
            updateSelectionState();
        });
    });

    setSelectionMode(false);
})();
