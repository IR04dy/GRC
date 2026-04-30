/**
 * i18n.js — Client-side language switcher with RTL support.
 *
 * - Reads language preference from localStorage ('grc_lang')
 * - Swaps Bootstrap LTR/RTL CSS without page reload
 * - Applies `rtl-layout` class to <body> for custom RTL overrides in rtl.css
 * - Replaces text content of elements with [data-i18n] attributes
 *
 * Depends on translations.js being loaded first (defines window.TRANSLATIONS).
 */

const I18n = {

    _LANG_KEY: 'grc_lang',
    _BOOTSTRAP_LTR: 'https://cdn.jsdelivr.net/npm/bootstrap@5.3.3/dist/css/bootstrap.min.css',
    _BOOTSTRAP_RTL: 'https://cdn.jsdelivr.net/npm/bootstrap@5.3.3/dist/css/bootstrap.rtl.min.css',

    get current() {
        return localStorage.getItem(this._LANG_KEY) || 'en';
    },

    /**
     * Switch the UI language.
     * @param {'en'|'ar'} lang
     */
    set(lang) {
        if (lang !== 'en' && lang !== 'ar') lang = 'en';
        localStorage.setItem(this._LANG_KEY, lang);

        const isRtl = lang === 'ar';

        // Update <html> attributes
        document.documentElement.lang = lang;
        document.documentElement.dir  = isRtl ? 'rtl' : 'ltr';

        // Toggle RTL body class (triggers rtl.css overrides)
        document.body.classList.toggle('rtl-layout', isRtl);

        // Force explicit wrapper direction so switching back to LTR is reliable
        const wrapper = document.querySelector('.wrapper');
        if (wrapper) wrapper.style.flexDirection = isRtl ? 'row-reverse' : 'row';

        // Swap Bootstrap CSS
        const bsCss = document.getElementById('bootstrap-css');
        if (bsCss) bsCss.href = isRtl ? this._BOOTSTRAP_RTL : this._BOOTSTRAP_LTR;

        // Toggle rtl.css
        const rtlCss = document.getElementById('rtl-css');
        if (rtlCss) rtlCss.disabled = !isRtl;

        // Apply translations
        this.applyTranslations();

        // Notify rest of page (charts, dynamic content may need to re-render)
        document.dispatchEvent(new CustomEvent('languageChanged', { detail: { lang } }));
    },

    /**
     * Translate a key, with optional interpolation.
     * @param {string}  key
     * @param {object}  [vars]  e.g. { name: 'Ahmad' } replaces {{name}}
     */
    t(key, vars) {
        const translations = window.TRANSLATIONS || {};
        let text = translations[this.current]?.[key] ?? translations['en']?.[key] ?? key;
        if (vars) {
            Object.entries(vars).forEach(([k, v]) => {
                text = text.replaceAll(`{{${k}}}`, v);
            });
        }
        return text;
    },

    /**
     * Apply translations to all [data-i18n] elements in the document.
     */
    applyTranslations() {
        document.querySelectorAll('[data-i18n]').forEach(el => {
            const key  = el.dataset.i18n;
            const text = this.t(key);
            // Respect elements that use title/placeholder attributes
            if (el.dataset.i18nAttr) {
                el.setAttribute(el.dataset.i18nAttr, text);
            } else {
                el.textContent = text;
            }
        });
    },

    /**
     * Initialize on page load.
     */
    init() {
        this.set(this.current);
    }
};

// Auto-init when DOM is ready
document.addEventListener('DOMContentLoaded', () => I18n.init());

window.I18n = I18n;
