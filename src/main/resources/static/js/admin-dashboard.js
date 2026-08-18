(() => {
    const storageKey = "nightout.admin.selectedSection";
    const defaultSection = "create-club";
    const dashboard = document.querySelector("[data-admin-dashboard]");
    const panels = Array.from(document.querySelectorAll("[data-admin-panel]"));
    const navLinks = Array.from(document.querySelectorAll("[data-admin-section]"));
    let activeSection = defaultSection;

    function panelIds() {
        return new Set(panels.map((panel) => panel.dataset.adminPanel));
    }

    function isValidSection(section) {
        return panelIds().has(section);
    }

    function storeSection(section) {
        try {
            sessionStorage.setItem(storageKey, section);
        } catch (error) {
            // Storage can be unavailable in private or restricted browser modes.
        }
    }

    function takeStoredSection() {
        try {
            const stored = sessionStorage.getItem(storageKey);
            sessionStorage.removeItem(storageKey);
            return stored;
        } catch (error) {
            return null;
        }
    }

    function closeOffcanvas(link) {
        const offcanvas = link.closest(".offcanvas");
        if (!offcanvas || !window.bootstrap?.Offcanvas) return;
        const instance = window.bootstrap.Offcanvas.getOrCreateInstance(offcanvas);
        instance.hide();
    }

    function setActiveSection(section) {
        if (!isValidSection(section)) return;
        activeSection = section;
        panels.forEach((panel) => {
            panel.hidden = panel.dataset.adminPanel !== section;
        });
        navLinks.forEach((link) => {
            const isActive = link.dataset.adminSection === section;
            link.classList.toggle("active", isActive);
            if (isActive) {
                link.setAttribute("aria-current", "page");
            } else {
                link.removeAttribute("aria-current");
            }
        });
        document.body.classList.add("admin-dashboard-ready");
    }

    navLinks.forEach((link) => {
        link.addEventListener("click", (event) => {
            const section = link.dataset.adminSection;
            if (!section) return;
            if (!dashboard) {
                storeSection(section);
                return;
            }

            event.preventDefault();
            setActiveSection(section);
            closeOffcanvas(link);
        });
        link.setAttribute("href", "/admin");
    });

    if (!dashboard || panels.length === 0) return;

    const storedSection = takeStoredSection();
    setActiveSection(isValidSection(storedSection) ? storedSection : defaultSection);

    dashboard.querySelectorAll("form").forEach((form) => {
        form.addEventListener("submit", () => storeSection(activeSection));
    });
})();
