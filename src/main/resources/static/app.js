(function () {
  const TYPE_COLORS = {
    normal: '#A8A878',
    fire: '#F08030',
    water: '#6890F0',
    electric: '#F8D030',
    grass: '#78C850',
    ice: '#98D8D8',
    fighting: '#C03028',
    poison: '#A040A0',
    ground: '#E0C068',
    flying: '#A890F0',
    psychic: '#F85888',
    bug: '#A8B820',
    rock: '#B8A038',
    ghost: '#705898',
    dragon: '#7038F8',
    dark: '#705848',
    steel: '#B8B8D0',
    fairy: '#EE99AC'
  };
  const DEFAULT_TYPE_COLOR = '#68A090';
  const SUPPORTED_LANGUAGES = ['en', 'fr', 'de', 'es', 'it', 'ja', 'ko', 'zh-hans', 'zh-hant'];

  const BUTTON_LABELS = {
    en: 'New Random Pokémon',
    fr: 'Nouveau Pokémon aléatoire',
    de: 'Neues zufälliges Pokémon',
    es: 'Nuevo Pokémon aleatorio',
    it: 'Nuovo Pokémon casuale',
    ja: 'ランダムなポケモンを表示',
    ko: '무작위 포켓몬 보기',
    'zh-hans': '查看随机宝可梦',
    'zh-hant': '查看隨機寶可夢'
  };

  function detectBrowserLanguage() {
    const preferences = (navigator.languages && navigator.languages.length)
      ? navigator.languages
      : [navigator.language || 'en'];

    for (const tag of preferences) {
      const lower = tag.toLowerCase();
      if (lower.startsWith('zh')) {
        const isTraditional = lower.includes('hant') || /-(tw|hk|mo)\b/.test(lower);
        return isTraditional ? 'zh-hant' : 'zh-hans';
      }
      const base = lower.split('-')[0];
      if (SUPPORTED_LANGUAGES.includes(base)) {
        return base;
      }
    }
    return 'en';
  }

  const imageEl = document.getElementById('pokemon-image');
  const nameEl = document.getElementById('pokemon-name');
  const typesEl = document.getElementById('pokemon-types');
  const errorEl = document.getElementById('status-error');
  const buttonEl = document.getElementById('new-pokemon-button');
  const languageSelectEl = document.getElementById('language-select');

  let currentSlug = null;

  function updateButtonLabel() {
    buttonEl.textContent = BUTTON_LABELS[languageSelectEl.value] || BUTTON_LABELS.en;
  }

  function setControlsDisabled(disabled) {
    buttonEl.disabled = disabled;
    languageSelectEl.disabled = disabled;
  }

  function showLoading() {
    errorEl.hidden = true;
    setControlsDisabled(true);
  }

  function showPokemon(pokemon) {
    errorEl.hidden = true;
    currentSlug = pokemon.slug;

    nameEl.textContent = pokemon.name;

    if (pokemon.imageUrl) {
      imageEl.src = pokemon.imageUrl;
      imageEl.alt = pokemon.name;
      imageEl.hidden = false;
    } else {
      imageEl.hidden = true;
      imageEl.removeAttribute('src');
    }

    typesEl.innerHTML = '';
    (pokemon.types || []).forEach(function (type) {
      const badge = document.createElement('span');
      badge.className = 'type-badge';
      badge.textContent = type.name;
      badge.style.backgroundColor = TYPE_COLORS[type.slug] || DEFAULT_TYPE_COLOR;
      typesEl.appendChild(badge);
    });
  }

  function showError(message) {
    errorEl.hidden = false;
    errorEl.textContent = message;
  }

  async function loadPokemon(url) {
    showLoading();
    try {
      const response = await fetch(url);
      const data = await response.json();
      if (!response.ok) {
        throw new Error(data.error || 'Something went wrong.');
      }
      showPokemon(data);
    } catch (err) {
      showError(err.message || "Couldn't reach the Pokemon service — please try again.");
    } finally {
      setControlsDisabled(false);
    }
  }

  function fetchRandomPokemon() {
    return loadPokemon('/api/random-pokemon?lang=' + encodeURIComponent(languageSelectEl.value));
  }

  function relocalizeCurrentPokemon() {
    if (!currentSlug) {
      return;
    }
    return loadPokemon('/api/pokemon/' + encodeURIComponent(currentSlug) +
        '?lang=' + encodeURIComponent(languageSelectEl.value));
  }

  document.addEventListener('DOMContentLoaded', function () {
    languageSelectEl.value = detectBrowserLanguage();
    updateButtonLabel();
    fetchRandomPokemon();
  });

  buttonEl.addEventListener('click', fetchRandomPokemon);

  languageSelectEl.addEventListener('change', function () {
    updateButtonLabel();
    relocalizeCurrentPokemon();
  });
})();
