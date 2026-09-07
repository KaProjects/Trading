import {useCloseOnNavigation} from "../../service/NavigationService";

/**
 * Renders nothing. Lets a component that provides the router - and therefore cannot call
 * useLocation itself - close its dialogs on navigation.
 */
export const CloseOnNavigation = ({close}) => {
    useCloseOnNavigation(close)
    return null
}
