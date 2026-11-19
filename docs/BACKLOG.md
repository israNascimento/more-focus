# Backlog do MoreFocus

Hoje, Reels do Instagram e do Facebook, Shorts, vídeos em tela cheia do X e o feed Para Você do TikTok compartilham um limite diário. O MoreFocus também permite limitar o uso de um aplicativo inteiro. Os itens abaixo estão em ordem de prioridade proposta para as próximas versões.

## 1. Iniciar uma sessão de foco de 30 minutos

Quem vai estudar ou trabalhar pode guardar o saldo diário de vídeos para mais tarde.

- [ ] Na aba Uso, oferecer a ação **Foco por 30 minutos** e mostrar a que horas a sessão termina.
- [ ] Durante a sessão, bloquear a abertura de Reels e Shorts habilitados mesmo quando ainda houver saldo. Permitir responder a mensagens no Instagram e assistir a vídeos comuns no YouTube, respeitando eventuais limites do aplicativo inteiro.
- [ ] Ao terminar a sessão, voltar a aplicar o limite diário. O tempo passado com os vídeos bloqueados não consome saldo.

## 2. Agendar horários sem vídeos curtos

Quem tem uma rotina fixa pode programar a mesma proteção da sessão manual, por exemplo, de segunda a sexta, das 9h às 12h.

- [ ] Permitir escolher dias da semana, início e fim do período, além de editar ou desativar o agendamento.
- [ ] Ativar o bloqueio no início do horário e encerrá-lo ao final. Se uma sessão manual ainda estiver em andamento, manter a proteção até ela terminar.
- [ ] Verificar que a regra de segunda a sexta se aplica na sexta-feira e deixa o sábado livre.

## 3. Fazer uma pausa após alguns minutos seguidos

Uma pessoa com 20 minutos disponíveis no dia pode preferir assistir em sessões de 5 minutos.

- [ ] Permitir ativar um lembrete e escolher após quantos minutos contínuos de vídeos ele aparece.
- [ ] Ao atingir esse tempo, oferecer **Continuar** e **Sair dos vídeos**. Continuar inicia um novo intervalo e segue consumindo o saldo diário.
- [ ] Ao sair dos vídeos, encerrar a contagem contínua. Duas sessões de 3 minutos separadas por uma conversa no Instagram não devem disparar um lembrete de 5 minutos.

## 4. Tornar clara a saída dos vídeos dentro do aplicativo

Hoje, **Sair** já aciona o comando Voltar do Android. A melhoria é deixar claro o destino quando a pessoa pode retomar o que fazia antes de abrir o vídeo.

- [ ] Quando houver uma tela anterior identificável no mesmo app, oferecer uma ação como **Voltar ao Instagram** ou **Voltar ao YouTube** no aviso de limite de vídeos.
- [ ] Verificar o percurso de quem abre um Reel a partir de uma conversa e quer voltar a ela sem conceder **+1 minuto**.
- [ ] Testar também um vídeo aberto por link externo. Quando o destino for incerto, usar o rótulo **Voltar**.

## 5. Consultar um resumo semanal no aparelho

Quem consulta o resumo pode descobrir em quais dias assistiu a mais vídeos curtos e com que frequência pediu mais um minuto.

- [ ] Na aba Uso, mostrar os minutos de vídeos curtos e a quantidade de extensões **+1 minuto** concedidas em cada um dos últimos sete dias.
- [ ] Guardar os totais diários localmente. Cada extensão concedida conta uma vez, inclusive depois que a espera exponencial atinge o máximo.
- [ ] Distinguir um dia registrado com zero uso de um dia anterior ao início do histórico.

## 6. Incluir mais aplicativos no limite compartilhado

A primeira integração desta lista é o feed **Para Você** do TikTok. O limite por aplicativo inteiro já está disponível para quem quiser contar todo o tempo de uso.

- [x] **TikTok.** No Galaxy S21 FE com TikTok 47.7.3, o MoreFocus identifica a aba Para Você selecionada junto ao player. A contagem para ao abrir Mensagens ou Perfil. A aba Seguindo também fica livre.
- [x] **Integração do TikTok.** A regra do Para Você pode ser ativada nas Preferências. Com um limite de 15 minutos, assistir a 6 minutos de Reels, 4 de Shorts e 5 de TikTok deve acionar o mesmo aviso de limite atingido.
- [x] **Facebook Reels.** No Facebook 588.0.0.42.55, a regra reconhece a página de reprodução do Reel aberto em tela cheia. Prévias no feed e a navegação em Grupos e Marketplace ficam fora do limite compartilhado.
- [ ] **Kwai.** Testar se é possível distinguir o feed de vídeos das demais telas antes de liberar a integração.
- [ ] A cada integração entregue, atualizar os nomes exibidos nas preferências, no resumo de uso e na explicação de acessibilidade para refletir quais aplicativos o MoreFocus reconhece.

Para validar os fluxos que dependem de outros aplicativos, usar o aparelho Android físico conectado ao projeto.
