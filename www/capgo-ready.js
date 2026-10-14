// Avisa o atualizador (Capgo) que esta versão da tela abriu normalmente.
// Sem esse aviso, o Capgo acha que a versão nova está quebrada e volta para a anterior.
// Só existe dentro do app instalado; no navegador não faz nada.
(function () {
  try {
    if (window.Capacitor && window.Capacitor.Plugins && window.Capacitor.Plugins.CapacitorUpdater) {
      window.Capacitor.Plugins.CapacitorUpdater.notifyAppReady();
    }
  } catch (e) {}
})();
